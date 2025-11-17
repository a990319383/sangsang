package com.sangsang.interceptor;

import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import com.sangsang.cache.encryptor.EncryptorInstanceCache;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.annos.FieldInterceptorOrder;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.annos.encryptor.PoJoResultEncryptor;
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
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 采用java 函数对pojo处理的加解密模式
 * 处理select的 响应语句
 *
 * @author liutangqi
 * @date 2024/7/9 14:06
 */
@FieldInterceptorOrder(InterceptorOrderConstant.ENCRYPTOR)
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
@Slf4j
public class PoJoResultEncrtptorInterceptor implements Interceptor, BeanPostProcessor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        //1.获取核心类(@Signature 后面的args顺序和下面获取的一致)
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        BoundSql boundSql = statement.getBoundSql(parameter);
        String originalSql = boundSql.getSql();

        //2.当前sql如果肯定不需要加解密，则不解析sql，直接返回
        if (StringUtils.notExist(originalSql, TableCache.getFieldEncryptTable())) {
            return invocation.proceed();
        }

        //3.解析sql,获取入参和响应对应的表字段关系
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair = parseSql(originalSql);

        //4.执行sql
        Object result = invocation.proceed();

        //5.处理响应
        return disposeResult(result, pair);
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
     * 将响应结果中需要解密的进行解密处理
     *
     * @author liutangqi
     * @date 2024/7/26 15:52
     * @Param [result, pair]
     **/
    private Object disposeResult(Object result, Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair) throws IllegalAccessException {
        //0.sql执行结果不是Collection直接返回(update insert语句执行时，结果不是Collection)
        if (!(result instanceof Collection)) {
            return result;
        }

        //1.sql执行结果为空，直接返回
        Collection<Object> resList = (Collection<Object>) result;
        if (CollectionUtils.isEmpty(resList)) {
            return resList;
        }

        //2.当前执行的sql 查询的所有字段信息
        List<FieldEncryptorInfoDto> fieldInfos = pair.getValue();

        //3.依次对结果的每一个字段进行处理
        List decryptorRes = new ArrayList();
        for (Object res : resList) {
            decryptorRes.add(decryptor(res, fieldInfos));
        }
        return decryptorRes;
    }

    /**
     * 对需要密文存储的对象属性进行解密
     *
     * @author liutangqi
     * @date 2024/7/26 16:24
     * @Param [res, fieldInfos]
     **/
    private Object decryptor(Object res, List<FieldEncryptorInfoDto> fieldInfos) throws IllegalAccessException {
        //0.整个对象都为null，直接返回
        if (res == null) {
            return res;
        }

        //1.基础数据类型对应的包装类或字符串或时间类型
        if (FieldConstant.FUNDAMENTAL.contains(res.getClass())) {
            //1.1 响应类型是字符串，并且该sql 查询结果只有一个字段
            if (res instanceof String && fieldInfos.size() == 1) {
                FieldEncryptor fieldEncryptor = fieldInfos.get(0).getFieldEncryptor();
                if (fieldEncryptor != null) {
                    res = EncryptorInstanceCache.<String>getInstance(fieldEncryptor.value()).decryption((String) res);
                    return res;
                }
            }
        }
        //2.响应类型是Map
        else if (res instanceof Map) {
            Map resMap = (Map) res;
            for (Map.Entry<String, Object> entry : (Set<Map.Entry<String, Object>>) resMap.entrySet()) {
                FieldEncryptor fieldEncryptor = getFieldEncryptorByFieldName(entry.getKey(), fieldInfos);
                if (fieldEncryptor != null) {
                    entry.setValue(EncryptorInstanceCache.<String>getInstance(fieldEncryptor.value()).decryption((String) entry.getValue()));
                }
            }
        }
        //3.响应类型是其它实体类
        else {
            List<Field> allFields = ReflectUtils.getNotStaticFinalFields(res.getClass());
            for (Field field : allFields) {
                //优先取响应实体类字段上面的@PoJoResultEncryptor 的信息 ，取不到再根据实体类上面标注的信息取
                PoJoResultEncryptor poJoResultEncryptor = field.getAnnotation(PoJoResultEncryptor.class);
                FieldEncryptor fieldEncryptor = getFieldEncryptorByFieldName(field.getName(), fieldInfos);
                if (poJoResultEncryptor != null || fieldEncryptor != null) {
                    field.setAccessible(true);
                    field.set(res, EncryptorInstanceCache.<String>getInstance(fieldEncryptor.value()).decryption((String) field.get(res)));
                }
            }
        }
        return res;
    }

    /**
     * 根据字段名字从sql解析结果中，找到该实体类上面标准的注解信息
     * 注意：fieldName是驼峰的，fieldInfos 中的别名也可能是驼峰，可能是下划线，这里做个自动转
     *
     * @author liutangqi
     * @date 2024/7/26 16:07
     * @Param [fieldName, fieldInfos]
     **/
    private FieldEncryptor getFieldEncryptorByFieldName(String fieldName, List<FieldEncryptorInfoDto> fieldInfos) {
        //从所有字段中查到这个字段的信息（理论上只会存在一个）
        return fieldInfos.stream()
                .filter(f -> StringUtils.fieldEquals(fieldName, f.getColumnName())
                        || StringUtils.fieldEquals(fieldName, StrUtil.toCamelCase(f.getColumnName())))
                .findAny()
                .map(FieldEncryptorInfoDto::getFieldEncryptor)
                .orElse(null);
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
                    .filter(f -> PoJoResultEncrtptorInterceptor.class.isAssignableFrom(f.getClass()))
                    .findAny()
                    .orElse(null) == null) {
                sessionFactory.getConfiguration().addInterceptor(new PoJoResultEncrtptorInterceptor());
                log.info("【field-encryptor】手动注册拦截器 PoJoResultEncrtptorInterceptor");
            }

            //修改拦截器顺序
            InterceptorUtil.sort(sessionFactory.getConfiguration());
        }
        return bean;
    }

}
