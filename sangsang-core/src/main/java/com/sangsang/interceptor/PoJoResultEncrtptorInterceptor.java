package com.sangsang.interceptor;

import cn.hutool.core.lang.Pair;
import com.sangsang.cache.encryptor.EncryptorInstanceCache;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.annos.FieldInterceptorOrder;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.annos.encryptor.PoJoResultEncryptor;
import com.sangsang.domain.constants.FieldConstant;
import com.sangsang.domain.constants.InterceptorOrderConstant;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldEncryptorInfoDto;
import com.sangsang.domain.strategy.encryptor.FieldEncryptorStrategy;
import com.sangsang.domain.wrapper.ClassHashMapWrapper;
import com.sangsang.domain.wrapper.MappingHashMapWrapper;
import com.sangsang.util.*;
import com.sangsang.visitor.pojoencrtptor.PoJoEncrtptorStatementVisitor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 采用java 函数对pojo处理的加解密模式
 * 处理select的 响应语句
 *
 * @author liutangqi
 * @date 2024/7/9 14:06
 */
@FieldInterceptorOrder(InterceptorOrderConstant.ENCRYPTOR)
@Intercepts({@Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {java.sql.Statement.class})})
@Slf4j
public class PoJoResultEncrtptorInterceptor implements Interceptor, BeanPostProcessor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        //1.通过反射获取 获取核心对象
        Pair<BoundSql, MappedStatement> parseCore = parseCore(invocation);

        //2.当前sql如果肯定不需要加解密，则不解析sql，直接返回
        if (StringUtils.notExist(parseCore.getKey().getSql(), TableCache.getFieldEncryptTable())) {
            return invocation.proceed();
        }

        //3.解析sql,获取入参和响应对应的表字段关系
        Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair = parseSql(parseCore.getKey().getSql());

        //4.执行sql
        Object result = invocation.proceed();

        //5.处理响应
        return disposeResult(result, pair, parseCore.getValue());
    }


    /**
     * 通过反射获取到一些需要的核心对象
     *
     * @author liutangqi
     * @date 2025/12/26 13:24
     * @Param [invocation]
     **/
    private Pair<BoundSql, MappedStatement> parseCore(Invocation invocation) {
        //1. 获取目标对象 (ResultSetHandler)
        ResultSetHandler resultSetHandler = (ResultSetHandler) invocation.getTarget();

        //2. 使用 MyBatis 的 MetaObject 方便地访问私有属性
        MetaObject metaObject = InterceptorUtil.forObject(resultSetHandler);

        //3. 从 DefaultResultSetHandler 中提取 boundSql
        BoundSql boundSql = (BoundSql) metaObject.getValue("boundSql");

        //4.获取目标对象 (MappedStatement)
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("mappedStatement");

        //5.返回结果
        return new Pair<>(boundSql, mappedStatement);
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
    private Object disposeResult(Object result,
                                 Pair<Map<String, ColumnTableDto>, List<FieldEncryptorInfoDto>> pair,
                                 MappedStatement mappedStatement
    ) throws IllegalAccessException {
        //0.sql执行结果不是Collection直接返回(update insert语句执行时，结果不是Collection)
        if (!(result instanceof Collection)) {
            return result;
        }

        //1.sql执行结果为空，直接返回
        Collection<Object> resList = (Collection<Object>) result;
        if (CollectionUtils.isEmpty(resList)) {
            return resList;
        }

        //2.解析出xml中配置的resultMap
        Map<String, Set<String>> resultMap = parseResultMap(mappedStatement);

        //3.将当前sql的解析结果以字段为key，进行存储，便于后续通过key快速匹配。使用MappingHashMapWrapper能屏蔽掉大小写差异，并根据当前配置进行下划线和驼峰的兼容
        Map<String, FieldEncryptorInfoDto> fieldEncryptorMap = new MappingHashMapWrapper(resultMap);
        for (FieldEncryptorInfoDto fieldEncryptorInfoDto : pair.getValue()) {
            fieldEncryptorMap.put(fieldEncryptorInfoDto.getColumnName(), fieldEncryptorInfoDto);
        }

        //4.通过反射，缓存这个映射类的字段信息，字段对应的解密策略信息，需要解密的密文集合
        //4.1创建缓存Map：用于缓存每个解密策略对应密文数据集（key:解密策略 value:对应的密文集合）
        Map<FieldEncryptorStrategy, Set<String>> fieldEncryptorStrategyMap = new HashMap<>();
        //4.2创建缓存Map：当响应是java类时，用于缓存每个类的所有字段信息（key:映射接受参数的java类 value:(key:field的name value:Field对象)）
        Map<Class, Map<String, Field>> clsFieldMap = new ClassHashMapWrapper<>();
        //4.3创建缓存Map：当响应是java类时，用于缓存每个类需要解密的字段信息(key:映射接受参数的java类 value:（key:field的name value:这个字段的加解密策略）)
        Map<Class, Map<String, FieldEncryptorStrategy>> clsFieldStrategyMap = new ClassHashMapWrapper<>();
        for (Object res : resList) {
            collectionCiphertext(fieldEncryptorStrategyMap, clsFieldStrategyMap, clsFieldMap, res, fieldEncryptorMap);

        }

        //5.批量解密
        Map<String, String> cleartextMap = batchDecryption(fieldEncryptorStrategyMap);

        //6.如果所有字段都不需要解密，则直接返回
        if (CollectionUtils.isEmpty(cleartextMap)) {
            return resList;
        }

        //7.用明文替换密文
        List<Object> decryptionRes = new ArrayList<>();
        for (Object res : resList) {
            decryptionRes.add(replaceCiphertext(cleartextMap, clsFieldStrategyMap, clsFieldMap, fieldEncryptorMap, res));
        }

        return decryptionRes;
    }

    /**
     * 获取到当前执行的mapper的resultMap配置
     * key：resultMap的 column (即sql查询结果的字段名)
     * value：resultMap的 property (即java类属性名，同一个resultMap中，一个column可以映射到多个property中，所以这里是个集合)
     *
     * @author liutangqi
     * @date 2026/8/19 17:41
     * @Param [mappedStatement]
     **/
    private Map<String, Set<String>> parseResultMap(MappedStatement mappedStatement) {
        Map<String, Set<String>> resMap = new HashMap<>();
        List<ResultMap> resultMaps = mappedStatement.getResultMaps();
        for (ResultMap resultMap : resultMaps) {
            List<ResultMapping> resultMappings = resultMap.getResultMappings();
            for (ResultMapping resultMapping : resultMappings) {
                CollectionUtils.putList(resMap, resultMapping.getColumn(), resultMapping.getProperty(), new HashSet<>());
            }
        }
        return resMap;
    }


    /**
     * 收集结果集中需要密文存储的字段集合，用于后续的批量解密做出准备
     *
     * @param fieldEncryptorStrategyMap 用于存放收集结果的容器 key:加解密策略实例 value:需要这个策略处理的字段
     * @param clsFieldStrategyMap       用于缓存每个类需要解密的字段策略信息(key:class value(key:field的name value:加解密策略实例)
     * @param clsFieldMap               缓存映射java类的每个字段 （key:class value(key:field的name value:field对象)）
     * @param res                       mapper的执行结果
     * @param fieldEncryptorMap         解析sql的结果集
     * @author liutangqi
     * @date 2026/8/19 17:41
     * @Param [fieldEncryptorStrategyMap,))
     * @author liutangqi
     * @date 2026/8/13 17:49
     **/
    private void collectionCiphertext(Map<FieldEncryptorStrategy, Set<String>> fieldEncryptorStrategyMap,
                                      Map<Class, Map<String, FieldEncryptorStrategy>> clsFieldStrategyMap,
                                      Map<Class, Map<String, Field>> clsFieldMap,
                                      Object res,
                                      Map<String, FieldEncryptorInfoDto> fieldEncryptorMap)
            throws IllegalAccessException {

        //0.整个对象都为null，直接返回
        if (res == null) {
            return;
        }

        //1.基础数据类型对应的包装类或字符串或时间类型
        if (FieldConstant.FUNDAMENTAL.contains(res.getClass())) {
            //1.1 响应类型是字符串，并且该sql 查询结果只有一个字段
            //PS: 对应语法 List<String> xxxMapper();  select xxx from tb_xxx; 查询结果只有一个字段，返回的也是字符串
            if (res instanceof String && fieldEncryptorMap.size() == 1) {
                FieldEncryptor fieldEncryptor = fieldEncryptorMap.values().stream().findAny().get().getFieldEncryptor();
                if (fieldEncryptor != null) {
                    CollectionUtils.putList(fieldEncryptorStrategyMap, EncryptorInstanceCache.<String>getInstance(fieldEncryptor.value()), (String) res, new HashSet<>());
                }
            }
        }
        //2.响应类型是Map
        else if (res instanceof Map) {
            Map resMap = (Map) res;
            for (Map.Entry<String, Object> entry : (Set<Map.Entry<String, Object>>) resMap.entrySet()) {
                FieldEncryptor fieldEncryptor = getFieldEncryptorByFieldName(entry.getKey(), fieldEncryptorMap);
                if (fieldEncryptor != null) {
                    CollectionUtils.putList(fieldEncryptorStrategyMap, EncryptorInstanceCache.<String>getInstance(fieldEncryptor.value()), (String) entry.getValue(), new HashSet<>());
                }
            }
        }
        //3.响应类型是其它实体类
        else {
            Map<String, FieldEncryptorStrategy> fieldStrategyMap = clsFieldStrategyMap.get(res.getClass());
            //3.1 当前类未反射获取过字段信息
            if (fieldStrategyMap == null) {
                Map<String, FieldEncryptorStrategy> currentFieldStrategyMap = new HashMap<>();
                //3.1.1 反射获取所有字段
                List<Field> notStaticFinalFields = ReflectUtils.getNotStaticFinalFields(res.getClass());
                for (Field field : notStaticFinalFields) {
                    //3.1.2 缓存当前类的字段信息
                    CollectionUtils.putMap(clsFieldMap, res.getClass(), field.getName(), field, new HashMap<>());

                    //优先取响应实体类字段上面的@PoJoResultEncryptor 的信息 ，取不到再根据实体类上面标注的信息取
                    Class<? extends FieldEncryptorStrategy> poJoResultEncryptorCls = Optional.ofNullable(field.getAnnotation(PoJoResultEncryptor.class)).map(PoJoResultEncryptor::value).orElse(null);
                    Class<? extends FieldEncryptorStrategy> fieldEncryptorCls = Optional.ofNullable(getFieldEncryptorByFieldName(field.getName(), fieldEncryptorMap)).map(FieldEncryptor::value).orElse(null);
                    Class<? extends FieldEncryptorStrategy> encryptorStrategyCls = poJoResultEncryptorCls != null ? poJoResultEncryptorCls : fieldEncryptorCls;
                    if (encryptorStrategyCls != null) {
                        //3.1.3 缓存当前字段对应策略
                        currentFieldStrategyMap.put(field.getName(), EncryptorInstanceCache.<FieldEncryptorStrategy>getInstance(encryptorStrategyCls));

                        //记录当前字段的策略和实际值，用于后续批量解密
                        field.setAccessible(true);
                        String fieldValue = Optional.ofNullable(field.get(res)).map(Object::toString).orElse(null);
                        //3.1.4 缓存当前需要解密的明文值，后于后续批量解密
                        CollectionUtils.putList(fieldEncryptorStrategyMap, EncryptorInstanceCache.<String>getInstance(encryptorStrategyCls), fieldValue, new HashSet<>());
                    }
                }
                clsFieldStrategyMap.put(res.getClass(), currentFieldStrategyMap);
            }
            //3.2 当前类已经反射获取过字段信息
            else {
                //3.2.1 获取当前类的全部字段信息（当前字段存在解密策略，说明肯定反射缓存过字段了，所以fieldMap一定不为空）
                Map<String, Field> fieldMap = clsFieldMap.get(res.getClass());
                for (Map.Entry<String, FieldEncryptorStrategy> entry : fieldStrategyMap.entrySet()) {
                    //3.2.2 缓存当前对象的密文字段，用于后续批量解密
                    Field field = fieldMap.get(entry.getKey());
                    field.setAccessible(true);
                    String fieldValue = Optional.ofNullable(field.get(res)).map(Object::toString).orElse(null);
                    CollectionUtils.putList(fieldEncryptorStrategyMap, entry.getValue(), fieldValue, new HashSet<>());
                }
            }
        }
    }

    /**
     * 使用明文替换结果集中的密文
     *
     * @param cleartextMap        批量解密后的结果集 key:密文 value:明文
     * @param clsFieldStrategyMap 用于缓存每个类需要解密的字段策略信息(key:class value(key:field的name value:加解密策略实例)
     * @param clsFieldMap         缓存映射java类的每个字段 （key:class value(key:field的name value:field对象)）
     * @param fieldEncryptorMap   解析sql的结果集
     * @param res                 mapper的执行结果
     * @author liutangqi
     * @date 2026/8/14 13:49
     **/
    private Object replaceCiphertext(Map<String, String> cleartextMap,
                                     Map<Class, Map<String, FieldEncryptorStrategy>> clsFieldStrategyMap,
                                     Map<Class, Map<String, Field>> clsFieldMap,
                                     Map<String, FieldEncryptorInfoDto> fieldEncryptorMap,
                                     Object res)
            throws IllegalAccessException {

        //0.整个对象都为null，直接返回
        if (res == null) {
            return res;
        }

        //1.基础数据类型对应的包装类或字符串或时间类型
        if (FieldConstant.FUNDAMENTAL.contains(res.getClass())) {
            //1.1 响应类型是字符串，并且该sql 查询结果只有一个字段
            //PS: 对应语法 List<String> xxxMapper();  select xxx from tb_xxx; 查询结果只有一个字段，返回的也是字符串
            if (res instanceof String && fieldEncryptorMap.size() == 1) {
                FieldEncryptor fieldEncryptor = fieldEncryptorMap.values().stream().findAny().get().getFieldEncryptor();
                if (fieldEncryptor != null) {
                    return cleartextMap.get((String) res);
                }
            }
        }
        //2.响应类型是Map
        else if (res instanceof Map) {
            Map resMap = (Map) res;
            for (Map.Entry<String, Object> entry : (Set<Map.Entry<String, Object>>) resMap.entrySet()) {
                FieldEncryptor fieldEncryptor = getFieldEncryptorByFieldName(entry.getKey(), fieldEncryptorMap);
                if (fieldEncryptor != null) {
                    resMap.put(entry.getKey(), cleartextMap.get((String) entry.getValue()));
                }
            }
            return resMap;
        }
        //3.响应类型是其它实体类，上面搜集字段信息批量解密时已经反射获取到所有需要解密的字段信息了，这里直接使用缓存值
        else {
            //3.1 缓存中获取当前类需要解密的字段和策略信息
            Map<String, FieldEncryptorStrategy> fieldStrategyMap = clsFieldStrategyMap.getOrDefault(res.getClass(), CollectionUtils.EMPTY_MAP);
            for (Map.Entry<String, FieldEncryptorStrategy> entry : fieldStrategyMap.entrySet()) {
                //3.2 从缓存里面获取字段值
                Field field = clsFieldMap.get(res.getClass()).get(entry.getKey());
                field.setAccessible(true);
                String fieldValue = Optional.ofNullable(field.get(res)).map(Object::toString).orElse(null);
                //3.3 从批量解密的结果集中拿到对应明文，替换对象的字段值
                field.set(res, cleartextMap.getOrDefault(fieldValue, fieldValue));
            }
            return res;
        }
        return res;
    }

    /**
     * 将这批密文根据策略进行批量解密
     *
     * @author liutangqi
     * @date 2026/8/14 13:36
     * @Param [fieldEncryptorStrategyMap]
     **/
    private Map<String, String> batchDecryption(Map<FieldEncryptorStrategy, Set<String>> fieldEncryptorStrategyMap) {
        Map<String, String> resMap = new HashMap<>();
        for (Map.Entry<FieldEncryptorStrategy, Set<String>> strategySetEntry : fieldEncryptorStrategyMap.entrySet()) {
            resMap.putAll(strategySetEntry.getKey().batchDecryption(strategySetEntry.getValue().stream().collect(Collectors.toList())));
        }
        return resMap;
    }

    /**
     * 根据字段名字从sql解析结果中，找到该实体类上面标准的注解信息
     * 注意：fieldName是驼峰的，fieldInfos 中的别名也可能是驼峰，可能是下划线，这里做个自动转
     *
     * @author liutangqi
     * @date 2024/7/26 16:07
     * @Param [fieldName, fieldEncryptorMap]
     **/
    private FieldEncryptor getFieldEncryptorByFieldName(String fieldName,
                                                        Map<String, FieldEncryptorInfoDto> fieldEncryptorMap) {
        return Optional.ofNullable(fieldEncryptorMap.get(fieldName))
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
                log.info("【sangsang】手动注册拦截器 PoJoResultEncrtptorInterceptor");
            }

            //修改拦截器顺序
            InterceptorUtil.sort(sessionFactory.getConfiguration());
        }
        return bean;
    }

}
