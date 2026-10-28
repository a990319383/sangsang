package com.sangsang.interceptor;

import cn.hutool.core.lang.Pair;
import com.sangsang.cache.encryptor.EncryptorInstanceCache;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.annos.FieldInterceptorOrder;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.constants.FieldConstant;
import com.sangsang.domain.constants.InterceptorOrderConstant;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldEncryptorInfoDto;
import com.sangsang.domain.exception.FieldException;
import com.sangsang.domain.strategy.encryptor.FieldEncryptorStrategy;
import com.sangsang.typehandler.PoJoParamEncryptorTypeHandler;
import com.sangsang.util.InterceptorUtil;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.StringUtils;
import com.sangsang.visitor.pojoencrtptor.PoJoEncrtptorStatementVisitor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.*;

/**
 * 采用java 函数对pojo处理的加解密模式
 * 处理入参
 *
 * @author liutangqi
 * @date 2024/7/9 14:06
 */
@FieldInterceptorOrder(InterceptorOrderConstant.ENCRYPTOR)
@Intercepts({@Signature(type = StatementHandler.class, method = "parameterize", args = {java.sql.Statement.class})})
@Slf4j
public class PoJoParamEncrtptorInterceptor implements Interceptor, BeanPostProcessor {

    /**
     * 将入参的字段和占位符？ 对应起来  （boundSql.getParameterMappings()获取的参数和占位符的顺序是一致的，jsqlparser的JdbcParameter中存储这个顺序的序号）
     *
     * @author liutangqi
     * @date 2024/7/18 14:45
     * @Param [invocation]
     **/
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        //1.获取基础信息
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = statementHandler.getBoundSql();
        String originalSql = boundSql.getSql();

        //2.当前sql如果肯定不需要加解密，则不解析sql，直接返回
        if (StringUtils.notExist(originalSql, TableCache.getFieldEncryptTable())) {
            return invocation.proceed();
        }

        //3.解析sql,获取入参和响应对应的表字段关系
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair = parseSql(originalSql);

        //4.处理入参
        disposeParam(statementHandler, boundSql, pair);

        //5.执行sql
        Object proceed = invocation.proceed();

        //6.返回结果
        return proceed;
    }

    /**
     * 解析sql,获取入参和响应对应的表字段关系
     *
     * @author liutangqi
     * @date 2024/7/18 14:55
     * @Param [sql]
     **/
    private Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseSql(String sql) throws JSQLParserException {
        //1.解析sql的响应结果，和占位符对应的表字段关系
        Statement statement = JsqlparserUtil.parse(sql);
        PoJoEncrtptorStatementVisitor poJoEncrtptorStatementVisitor = new PoJoEncrtptorStatementVisitor();
        statement.accept(poJoEncrtptorStatementVisitor);

        //2.获取解析结果
        Map<String, ColumnTableDto> placeholderColumnTableMap = poJoEncrtptorStatementVisitor.getPlaceholderColumnTableMap();
        List<FieldEncryptorInfoDto> fieldEncryptorInfos = poJoEncrtptorStatementVisitor.getFieldEncryptorInfos();
        return Pair.of(placeholderColumnTableMap, fieldEncryptorInfos);
    }

    /**
     * 将入参中需要加密的进行加密处理
     *
     * @author liutangqi
     * @date 2024/7/18 15:18
     * @Param [statementHandler, boundSql, pair]
     **/
    private void disposeParam(StatementHandler statementHandler, BoundSql boundSql, Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair) {
        //1.获取所有入参（这个的顺序和占位符顺序一致）
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

        //2.为需要加密的参数构建独立的ParameterMapping，避免相同property的参数互相覆盖
        List<ParameterMapping> encryptorParameterMappings = new ArrayList<>(parameterMappings.size());
        Configuration configuration = null;
        boolean changed = false;
        for (int i = 0; i < parameterMappings.size(); i++) {
            ParameterMapping parameterMapping = parameterMappings.get(i);
            //2.1 sql关系中，占位符对应的映射结果集的key规则是这个
            String placeholderKey = FieldConstant.PLACEHOLDER + i;
            //2.2 获取当前参数对应字段的加解密策略
            FieldEncryptor fieldEncryptor = parseFieldEncryptor(placeholderKey, pair.getKey());
            //2.3 当前参数不需要加密，则直接添加到新的ParameterMappings中
            if (fieldEncryptor == null || parameterMapping.getTypeHandler() instanceof PoJoParamEncryptorTypeHandler) {
                encryptorParameterMappings.add(parameterMapping);
                continue;
            }
            //2.4 当前未初始化过configuration，反射获取，并缓存部分配置
            if (configuration == null) {
                //2.4.1 反射获取
                configuration = parseConfiguration(statementHandler, parameterMapping);
                //2.4.2 缓存当前配置
                cacheConfiguration(configuration);
            }
            //2.5 使用新的TypeHandler，构建新的ParameterMapping
            FieldEncryptorStrategy<String> strategy = EncryptorInstanceCache.<String>getInstance(fieldEncryptor.value());
            TypeHandler<?> typeHandler = new PoJoParamEncryptorTypeHandler(parameterMapping.getTypeHandler(), strategy, i, parameterMapping.getProperty());
            encryptorParameterMappings.add(copyParameterMapping(configuration, parameterMapping, typeHandler));
            //2.6 标记当前修改过ParameterMappings
            changed = true;
        }

        //3.只修改当前BoundSql的ParameterMapping集合，不修改原始入参对象和缓存key
        if (changed) {
            InterceptorUtil.forObject(boundSql).setValue("parameterMappings", encryptorParameterMappings);
        }
    }

    /**
     * 获取当前StatementHandler所属的Configuration
     * 兼容Mybatis默认的RoutingStatementHandler和直接实现StatementHandler的场景
     *
     * @author liutangqi
     * @date 2026/10/28 10:45
     * @Param [statementHandler, parameterMapping]
     **/
    private Configuration parseConfiguration(StatementHandler statementHandler, ParameterMapping parameterMapping) {
        MetaObject parameterMappingMetaObject = InterceptorUtil.forObject(parameterMapping);
        if (parameterMappingMetaObject.hasGetter("configuration")) {
            try {
                Object configuration = parameterMappingMetaObject.getValue("configuration");
                if (configuration instanceof Configuration) {
                    return (Configuration) configuration;
                }
            } catch (Exception e) {
                log.debug("【sangsang】从ParameterMapping获取Configuration失败 class:{}", parameterMapping.getClass().getName(), e);
            }
        }

        MetaObject statementHandlerMetaObject = InterceptorUtil.forObject(statementHandler);
        String[] mappedStatementPaths = {"delegate.mappedStatement.configuration", "mappedStatement.configuration"};
        for (String mappedStatementPath : mappedStatementPaths) {
            if (statementHandlerMetaObject.hasGetter(mappedStatementPath)) {
                try {
                    Object value = statementHandlerMetaObject.getValue(mappedStatementPath);
                    if (value instanceof Configuration) {
                        return (Configuration) value;
                    }
                } catch (Exception e) {
                    log.debug("【sangsang】从StatementHandler获取Configuration失败 class:{} path:{}", statementHandler.getClass().getName(), mappedStatementPath, e);
                }
            }
        }

        throw new FieldException(String.format("无法获取Mybatis Configuration statementHandler:%s", statementHandler.getClass().getName()));
    }

    /**
     * 复制ParameterMapping，并使用入参加密TypeHandler包装原TypeHandler
     *
     * @author liutangqi
     * @date 2026/10/28 10:50
     * @Param [configuration, source, typeHandler]
     **/
    private ParameterMapping copyParameterMapping(Configuration configuration, ParameterMapping source, TypeHandler<?> typeHandler) {
        return new ParameterMapping.Builder(configuration, source.getProperty(), typeHandler).mode(source.getMode()).javaType(source.getJavaType()).jdbcType(source.getJdbcType()).numericScale(source.getNumericScale()).resultMapId(source.getResultMapId()).jdbcTypeName(source.getJdbcTypeName()).expression(source.getExpression()).build();
    }

    /**
     * 如果当前没有配置mapUnderscoreToCamelCase 信息，则从Configuration对象中进行解析并缓存
     *
     * @author liutangqi
     * @date 2026/10/16 14:37
     * @Param [metaObject]
     **/
    private void cacheConfiguration(Configuration configuration) {
        //有配置或者之前缓存过了，则不处理
        if (TableCache.getCurConfig().getEncryptor().getMapUnderscoreToCamelCase() != null) {
            return;
        }
        TableCache.getCurConfig().getEncryptor().setMapUnderscoreToCamelCase(configuration.isMapUnderscoreToCamelCase());
    }

    /**
     * 根据占位符名字获取sql解析结果集中字段上的注解
     *
     * @author liutangqi
     * @date 2024/9/20 17:19
     * @Param [placeholderKey, placeholderColumnTableMap]
     **/
    private FieldEncryptor parseFieldEncryptor(String placeholderKey, Map<String, ColumnTableDto> placeholderColumnTableMap) {
        ColumnTableDto columnTableDto = placeholderColumnTableMap.getOrDefault(placeholderKey, ColumnTableDto.DEAFAULT);
        return JsqlparserUtil.parseFieldEncryptor(columnTableDto);
    }

    /**
     * 低版本mybatis 这个方法不是default 方法，会报错找不到实现方法，所以这里实现默认的方法
     *
     * @author liutangqi
     * @date 2024/9/9 17:38
     * @Param [target]
     **/
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
    }


    /**
     * 实现父类default方法，避免低版本不兼容，找不到实现类
     *
     * @author liutangqi
     * @date 2024/9/10 11:36
     * @Param [bean, beanName]
     **/
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /**
     * 实现父类default方法，避免低版本不兼容，找不到实现类
     *
     * @author liutangqi
     * @date 2024/9/10 11:36
     * @Param [bean, beanName]
     **/
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        //当前没有注册此拦截器，则手动注册，避免有些项目自定义了SqlSessionFactory 导致拦截器漏注册
        //使用@Bean的方式注册，可能会导致某些项目的@PostContruct先于拦截器执行，导致拦截器业务代码失效
        if (SqlSessionFactory.class.isAssignableFrom(bean.getClass())) {
            SqlSessionFactory sessionFactory = (SqlSessionFactory) bean;
            if (sessionFactory.getConfiguration().getInterceptors()
                    .stream()
                    .filter(f -> PoJoParamEncrtptorInterceptor.class.isAssignableFrom(f.getClass()))
                    .findAny()
                    .orElse(null) == null) {
                sessionFactory.getConfiguration().addInterceptor(new PoJoParamEncrtptorInterceptor());
                log.info("【sangsang】手动注册拦截器 PoJoParamEncrtptorInterceptor");
            }

            //修改拦截器顺序
            InterceptorUtil.sort(sessionFactory.getConfiguration());
        }
        return bean;
    }

}
