package com.sangsang.interceptor;

import cn.hutool.core.lang.Pair;
import com.sangsang.cache.desensitize.DesensitizeInstanceCache;
import com.sangsang.domain.annos.FieldInterceptorOrder;
import com.sangsang.domain.annos.desensitize.FieldDesensitize;
import com.sangsang.domain.annos.desensitize.MapperDesensitize;
import com.sangsang.domain.constants.FieldConstant;
import com.sangsang.domain.constants.InterceptorOrderConstant;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldEncryptorInfoDto;
import com.sangsang.util.*;
import com.sangsang.visitor.pojoencrtptor.PoJoEncrtptorStatementVisitor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 数据脱敏的拦截器
 *
 * @author liutangqi
 * @date 2025/4/8 9:51
 */
@FieldInterceptorOrder(InterceptorOrderConstant.DESENSITIZE)
@Intercepts({@Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {java.sql.Statement.class})})
@Slf4j
public class FieldDesensitizeInterceptor implements Interceptor, BeanPostProcessor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        //1.获取核心类(@Signature 后面的args顺序和下面获取的一致)
        MappedStatement statement = parseMappedStatement(invocation);

        //2.解析mapper上面的@FieldDesensitize注解
        MapperDesensitize mapperFieldDesensitize = parseMapperAnno(statement);

        //3.执行sql
        Object result = invocation.proceed();

        //4.处理响应
        return disposeResult(result, mapperFieldDesensitize);
    }


    /**
     * 通过反射获取到MappedStatement
     *
     * @author liutangqi
     * @date 2025/12/26 13:44
     * @Param [invocation]
     **/
    private MappedStatement parseMappedStatement(Invocation invocation) {
        // 1.获取 ResultSetHandler 目标对象
        ResultSetHandler resultSetHandler = (ResultSetHandler) invocation.getTarget();

        //2.反射获取私有属性 mappedStatement
        MetaObject metaObject = InterceptorUtil.forObject(resultSetHandler);
        return (MappedStatement) metaObject.getValue("mappedStatement");

    }

    /**
     * 获取mapper上面的注解
     *
     * @author liutangqi
     * @date 2025/4/8 10:01
     * @Param [statement]
     **/
    private MapperDesensitize parseMapperAnno(MappedStatement statement) {
        MapperDesensitize mapperDesensitize = null;

        String id = statement.getId();
        try {
            Class<?> classType = Class.forName(id.substring(0, id.lastIndexOf(".")));
            String methodName = id.substring(id.lastIndexOf(".") + 1);
            mapperDesensitize = Arrays.asList(classType.getMethods())
                    .stream()
                    .filter(f -> f.getName().equals(methodName) && f.isAnnotationPresent(MapperDesensitize.class))
                    .map(m -> m.getAnnotation(MapperDesensitize.class))
                    .findAny()
                    .orElse(null);
        } catch (ClassNotFoundException e) {
            log.error("【sangsang】字段脱敏解析mapper异常", e);
        }

        return mapperDesensitize;
    }

    /**
     * 解析sql,获取入参和响应对应的表字段关系
     *
     * @author liutangqi
     * @date 2025/4/8 9:52
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
     * @date 2025/4/8 9:52
     * @Param [result :sql执行的结果, mapperFieldDesensitize:这个sql的mapper上面标注的@FieldDesensitize 注解]
     **/
    private Object disposeResult(Object result, MapperDesensitize mapperFieldDesensitize) throws IllegalAccessException, InstantiationException {
        //1.sql执行结果不是Collection直接返回(update insert语句执行时，结果不是Collection)
        if (!(result instanceof Collection)) {
            return result;
        }

        //2.sql执行结果为空，直接返回
        Collection<Object> resList = (Collection<Object>) result;
        if (CollectionUtils.isEmpty(resList)) {
            return resList;
        }

        //3.依次对结果的每一个字段进行处理
        List desensitizeRes = new ArrayList();
        for (Object res : resList) {
            desensitizeRes.add(desensitize(res, mapperFieldDesensitize));
        }
        return desensitizeRes;
    }

    /**
     * 将响应结果进行脱敏处理
     *
     * @author liutangqi
     * @date 2025/4/8 9:53
     * @Param [res :sql执行的结果,  mapperFieldDesensitize:这个sql的mapper上面标注的@FieldDesensitize 注解]
     **/
    private Object desensitize(Object res, MapperDesensitize mapperFieldDesensitize) throws IllegalAccessException {
        //0.整个对象都为null，直接返回
        if (res == null) {
            return res;
        }

        //1.基础数据类型对应的包装类或字符串或时间类型
        if (FieldConstant.FUNDAMENTAL.contains(res.getClass())) {
            //1.1 响应类型不是字符串，或者mapper上面标注的脱敏字段为空则不处理直接返回
            if (!(res instanceof String)
                    || Optional.ofNullable(mapperFieldDesensitize).map(MapperDesensitize::value).orElse(new FieldDesensitize[]{}).length != 1) {
                return res;
            }
            //1.2 根据注解上面标注的脱敏方法进行脱敏，然后返回
            return DesensitizeInstanceCache.getInstance(mapperFieldDesensitize.value()[0].value()).desensitize((String) res, res);
        }

        //2.响应类型是Map
        else if (res instanceof Map) {
            //2.1 mapper上面没有标注注解，或者标注的注解上面没有标注具体需要脱敏的字段则直接返回
            if (Optional.ofNullable(mapperFieldDesensitize).map(MapperDesensitize::value).orElse(new FieldDesensitize[]{}).length == 0) {
                return res;
            }
            //2.2 将mapper上面注解标注的字段key进行脱敏处理
            Map resMap = (Map) res;
            FieldDesensitize[] fieldDesensitizes = mapperFieldDesensitize.value();
            for (int i = 0; i < fieldDesensitizes.length; i++) {
                //2.2.1 没有指定Map的key,跳过这个，并输出警告日志
                FieldDesensitize fieldDesensitize = fieldDesensitizes[i];
                if (StringUtils.isBlank(fieldDesensitize.fieldName())) {
                    log.warn("【sangsang】响应类型为Map，脱敏时请指定字段名");
                    continue;
                }
                //2.2.2 将指定的key从结果集中取值，并进行脱敏处理后放到结果集中
                resMap.put(fieldDesensitize.fieldName(),
                        DesensitizeInstanceCache.getInstance(fieldDesensitize.value())
                                .desensitize(Optional.ofNullable(resMap.get(fieldDesensitize.fieldName())).map(Object::toString).orElse(null),
                                        res));
            }
        }

        //3.响应类型是其它实体类
        else {
            List<Field> allFields = ReflectUtils.getNotStaticFinalFields(res.getClass());
            for (Field field : allFields) {
                //获取字段上面标注的@FieldDesensitize
                FieldDesensitize fieldDesensitize = field.getAnnotation(FieldDesensitize.class);
                if (fieldDesensitize != null) {
                    field.setAccessible(true);
                    field.set(res,
                            DesensitizeInstanceCache.getInstance(fieldDesensitize.value())
                                    .desensitize(Optional.ofNullable(field.get(res)).map(Object::toString).orElse(null),
                                            res));
                }
            }
        }
        return res;
    }

    /**
     * 低版本mybatis 这个方法不是default 方法，会报错找不到实现方法，所以这里实现默认的方法
     *
     * @author liutangqi
     * @date 2025/4/8 9:53
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
     * @date 2025/4/8 9:53
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
     * @date 2025/4/8 9:53
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
                    .filter(f -> FieldDesensitizeInterceptor.class.isAssignableFrom(f.getClass()))
                    .findAny()
                    .orElse(null) == null) {
                sessionFactory.getConfiguration().addInterceptor(new FieldDesensitizeInterceptor());
                log.info("【sangsang】<desensitize>手动注册拦截器 FieldDesensitizeInterceptor");

                //修改拦截器顺序
                InterceptorUtil.sort(sessionFactory.getConfiguration());
            }
        }
        return bean;
    }
}
