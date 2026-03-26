package com.sangsang.interceptor;

import cn.hutool.core.lang.Pair;
import com.sangsang.cache.encryptor.EncryptorInstanceCache;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.annos.FieldInterceptorOrder;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.constants.FieldConstant;
import com.sangsang.domain.constants.InterceptorOrderConstant;
import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldEncryptorInfoDto;
import com.sangsang.util.InterceptorUtil;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.ReflectUtils;
import com.sangsang.util.StringUtils;
import com.sangsang.visitor.pojoencrtptor.PoJoEncrtptorStatementVisitor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.sql.Connection;
import java.util.*;

/**
 * 采用java 函数对pojo处理的加解密模式
 * 处理入参
 *
 * @author liutangqi
 * @date 2024/7/9 14:06
 */
@FieldInterceptorOrder(InterceptorOrderConstant.ENCRYPTOR)
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
@Slf4j
public class PoJoParamEncrtptorInterceptor implements Interceptor, BeanPostProcessor {

    /**
     * 将入参的字段和占位符？ 对应起来  （boundSql.getParameterMappings()获取的参数和占位符的顺序是一致的，这个结果集里面也有对应的占位符的key，这样就可以全部关联起来了）
     * 思路： 将boundsql 中的？ 占位符替换为 XXX特殊符号防重_1  XXX特殊符号防重_2  XXX特殊符号防重_3  这种，解析时就能得到占位符合参数的对应关系
     * 得到关系后再对请求参数进行加解密处理，因为这个时候我们已经知道该参数对应的数据库表字段是哪个了
     * 处理完后，将我们替换后的 _XXX特殊符号防重_1  这种重新替换为？  这样就能解决这个问题，并且不会存在破坏预编译sql导致sql注入的问题了
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
        disposeParam(boundSql, pair);

        //5.执行sql
        Object proceed = invocation.proceed();

        //6.返回结果
        return proceed;
    }

    /**
     * 将反射修改了property的给改回去，避免一级缓存导致找不到getter方法报错
     *
     * @author liutangqi
     * @date 2024/9/23 19:30
     * @Param [propertyMap, boundSql]
     **/
    private void revivificationParam(Map<String, String> propertyMap, BoundSql boundSql) {
        if (propertyMap == null || propertyMap.isEmpty()) {
            return;
        }

        for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
            String originalValue = propertyMap.get(parameterMapping.getProperty());
            if (StringUtils.isNotBlank(originalValue)) {
                //反射修改property为原值
                ReflectUtils.setFieldValue(parameterMapping, "property", originalValue);
            }
        }

    }


    /**
     * 解析sql,获取入参和响应对应的表字段关系
     *
     * @author liutangqi
     * @date 2024/7/18 14:55
     * @Param [sql]
     **/
    private Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> parseSql(String sql) throws JSQLParserException {
        //1.将sql中的 ? 占位符替换成我们自定义的特殊符号
        String placeholderSql = StringUtils.question2Placeholder(sql);

        //2.解析sql的响应结果，和占位符对应的表字段关系
        Statement statement = JsqlparserUtil.parse(placeholderSql);
        PoJoEncrtptorStatementVisitor poJoEncrtptorStatementVisitor = new PoJoEncrtptorStatementVisitor();
        statement.accept(poJoEncrtptorStatementVisitor);

        //3.获取解析结果
        Map<String, ColumnTableDto> placeholderColumnTableMap = poJoEncrtptorStatementVisitor.getPlaceholderColumnTableMap();
        List<FieldEncryptorInfoDto> fieldEncryptorInfos = poJoEncrtptorStatementVisitor.getFieldEncryptorInfos();
        return Pair.of(placeholderColumnTableMap, fieldEncryptorInfos);
    }

    /**
     * 将入参中需要加密的进行加密处理
     *
     * @author liutangqi
     * @date 2024/7/18 15:18
     * @Param [parameterObject, pair]
     **/
    private void disposeParam(BoundSql boundSql, Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair) {
        //1.获取所有入参（这个的顺序和占位符顺序一致）
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

        //2.将其中需要加密的字段进行加密(注意：这里只返回key value对应关系，不能现在就boundSql.setAdditionalParameter ，否则会导致 parseObj()方法中 hasAdditionalParameter()结果出错 aaa.bbb.ccc 这种方法只判断里面是否有aaa)
        Map<Integer, Object> parameterValue = new HashMap<>();
        for (int i = 0; i < parameterMappings.size(); i++) {
            ParameterMapping parameterMapping = parameterMappings.get(i);
            //sql关系中，占位符被统一替换成了这个
            String placeholderKey = FieldConstant.PLACEHOLDER + i;
            //获取当前映射字段的入参值
            Object propertyValue = InterceptorUtil.parseObj(boundSql, parameterMapping);

            //如果需要加密的话，将加密后的值，替换原有入参
            FieldEncryptor fieldEncryptor = parseFieldEncryptor(placeholderKey, pair.getKey());
            if (propertyValue instanceof String && fieldEncryptor != null) {
                String ciphertext = EncryptorInstanceCache.<String>getInstance(fieldEncryptor.value()).encryption((String) propertyValue);
                parameterValue.put(i, ciphertext);
            } else {
                //不需要加密的话，则入参还是使用旧值
                parameterValue.put(i, propertyValue);
            }
        }

        //3.将处理好的结果集进行设置值
        for (int i = 0; i < parameterMappings.size(); i++) {
            boundSql.setAdditionalParameter(parameterMappings.get(i).getProperty(), parameterValue.get(i));
        }
    }

    /**
     * 根据占位符名字获取sql解析结果集中字段上的注解
     *
     * @author liutangqi
     * @date 2024/9/20 17:19
     * @Param [placeholderKey, placeholderColumnTableMap]
     **/
    private FieldEncryptor parseFieldEncryptor(String placeholderKey, Map<String, ColumnTableDto> placeholderColumnTableMap) {
        ColumnTableDto columnTableDto = placeholderColumnTableMap.getOrDefault(placeholderKey, new ColumnTableDto());
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
