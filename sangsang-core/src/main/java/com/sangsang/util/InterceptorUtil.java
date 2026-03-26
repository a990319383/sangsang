package com.sangsang.util;

import com.sangsang.domain.annos.FieldInterceptorOrder;
import com.sangsang.domain.constants.FieldConstant;
import com.sangsang.domain.constants.InterceptorOrderConstant;
import com.sangsang.domain.constants.SymbolConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.InterceptorChain;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;

/**
 * 拦截器相关的工具类
 *
 * @author liutangqi
 * @date 2025/5/26 16:41
 */
@Slf4j
public class InterceptorUtil {

    /**
     * 将当前注册的拦截器进行排序
     *
     * @author liutangqi
     * @date 2025/5/26 17:01
     * @Param [configuration]
     **/
    public static void sort(Configuration configuration) {
        //1.先通过反射获取当前的所有拦截器
        InterceptorChain interceptorChain = (InterceptorChain) ReflectUtils.getFieldValue(configuration, "interceptorChain");
        List<Interceptor> interceptors = (List<Interceptor>) ReflectUtils.getFieldValue(interceptorChain, "interceptors");

        //2.将当前标注了@FieldInterceptorOrder 的拦截器和没有标注的分开
        List<Interceptor> selftInterceptor = interceptors.stream().filter(f -> f.getClass().isAnnotationPresent(FieldInterceptorOrder.class)).collect(Collectors.toList());
        List<Interceptor> otherInterceptor = interceptors.stream().filter(f -> !f.getClass().isAnnotationPresent(FieldInterceptorOrder.class)).collect(Collectors.toList());

        //3.将自己注册的拦截器根据@FieldInterceptorOrder 进行排序
        selftInterceptor.sort((o1, o2) -> Optional.ofNullable(o1.getClass().getAnnotation(FieldInterceptorOrder.class)).map(FieldInterceptorOrder::value).orElse(InterceptorOrderConstant.NORMAL) - Optional.ofNullable(o2.getClass().getAnnotation(FieldInterceptorOrder.class)).map(FieldInterceptorOrder::value).orElse(InterceptorOrderConstant.NORMAL));

        //4.将排序后的拦截器重新赋值给拦截器链
        interceptors.clear();
        interceptors.addAll(selftInterceptor);
        interceptors.addAll(otherInterceptor);
    }


    /**
     * 对mybtais的SystemMetaObject.forObject 进行一次包装
     * 使用前新代理溯源一次，避免代理对象无法获取到想要取的字段属性
     *
     * @author Gemini
     * @date 2025/12/26 14:24
     * @Param [obj]
     **/
    public static MetaObject forObject(Object obj) {
        // 使用 MyBatis 内置的 MetaObject来反射获取属性
        Object proxy = obj;
        MetaObject metaObject = SystemMetaObject.forObject(proxy);

        // 只要是 JDK 代理对象，通常都会包含一个 InvocationHandler (属性名为 h)
        // 而 MyBatis 的 Plugin 又是存在 h 里面的 target 属性中
        while (metaObject.hasGetter("h")) {
            Object h = metaObject.getValue("h");
            // 进一步判断这个 handler 是不是 MyBatis 的 Plugin
            if (h instanceof Plugin) {
                // Plugin 对象内部持有真正的 target
                proxy = SystemMetaObject.forObject(h).getValue("target");
                metaObject = SystemMetaObject.forObject(proxy);
            } else {
                // 如果是其他类型的代理且没有 target 属性，则无法继续脱壳
                break;
            }
        }
        return metaObject;
    }


    /**
     * 判断当前sql的mapper上面是否标注了注解T
     *
     * @author liutangqi
     * @date 2025/6/13 18:29
     * @Param [statementHandler, t]
     **/
    public static <T extends Annotation> T getMapperAnnotation(StatementHandler statementHandler, Class<? extends T> t) throws Exception {
        //类全限定名.方法名
        String nameSpace = getNameSpace(statementHandler);

        //以最后一个.为界，获取到类全限定名和方法名
        int lastDotIndex = nameSpace.lastIndexOf(SymbolConstant.FULL_STOP);
        String classFullyName = nameSpace.substring(0, lastDotIndex);
        String methodName = nameSpace.substring(lastDotIndex + 1);

        //反射判断是否存在此注解
        return Stream.of(Class.forName(classFullyName).getDeclaredMethods()).filter(f -> f.getName().equals(methodName)).findFirst().map(m -> m.getAnnotation(t)).orElse(null);
    }


    /**
     * 获取当前sql的namespace
     *
     * @author liutangqi
     * @date 2025/5/21 10:41
     * @Param [statementHandler]
     **/
    public static String getNameSpace(StatementHandler statementHandler) {
        //反射获取
        MetaObject metaObject = InterceptorUtil.forObject(statementHandler);
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        String id = mappedStatement.getId();
        return id;
    }


    /**
     * 解析获取映射对象的属性值
     *
     * @author liutangqi
     * @date 2024/7/24 14:49
     * @Param [configuration, boundSql, parameter]
     **/
    public static Object parseObj(BoundSql boundSql, ParameterMapping parameter) {
        Object obj = boundSql.getParameterObject();
        String property = parameter.getProperty();

        //0.判断boundsql中AdditionalParameter是否存在，存在就取boundsql中的(当入参在实体类中存在List时会走这段逻辑)
        if (boundSql.hasAdditionalParameter(property)) {
            return boundSql.getAdditionalParameter(property);
        }

        //1. 基本数据类型的包装类或者字符串或时间类型，直接返回原值
        if (FieldConstant.FUNDAMENTAL.contains(obj.getClass())) {
            return obj;
        }

        //2.其它类型的值，通过反射获取，如果入参是  dto.xxx 这种，则分开解析每一段，直至获取最终值
        String[] propertyArr = property.split(SymbolConstant.ESC_FULL_STOP);

        //上一层对象
        Object pre = obj;
        for (String prop : propertyArr) {
            pre = InterceptorUtil.forObject(pre).getValue(prop);
        }
        return pre;
    }

    /**
     * 获取当前执行sql使用的DataSource
     *
     * @author liutangqi
     * @date 2026/3/26 17:33
     * @Param [statementHandler]
     **/
    public static DataSource getCurrentDataSource(StatementHandler statementHandler) {
        MetaObject metaObject = InterceptorUtil.forObject(statementHandler);
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        if (mappedStatement == null
                || mappedStatement.getConfiguration() == null
                || mappedStatement.getConfiguration().getEnvironment() == null) {
            return null;
        }
        return unwrapDataSource(mappedStatement.getConfiguration().getEnvironment().getDataSource());
    }

    /**
     * 递归获取当前真正执行的DataSource
     *
     * @author liutangqi
     * @date 2026/3/26 17:33
     * @Param [dataSource]
     **/
    private static DataSource unwrapDataSource(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }

        DataSource targetDataSource = invokeDataSourceMethod(dataSource, "determineTargetDataSource");
        if (targetDataSource != null && targetDataSource != dataSource) {
            return unwrapDataSource(targetDataSource);
        }

        DataSource delegateDataSource = invokeDataSourceMethod(dataSource, "getTargetDataSource");
        if (delegateDataSource != null && delegateDataSource != dataSource) {
            return unwrapDataSource(delegateDataSource);
        }
        return dataSource;
    }

    /**
     * 反射调用DataSource上的无参方法
     *
     * @author liutangqi
     * @date 2026/3/26 17:33
     * @Param [dataSource, methodName]
     **/
    private static DataSource invokeDataSourceMethod(DataSource dataSource, String methodName) {
        try {
            Method method = getMethod(dataSource.getClass(), methodName);
            if (method == null || method.getParameterTypes().length != 0 || !DataSource.class.isAssignableFrom(method.getReturnType())) {
                return null;
            }
            method.setAccessible(true);
            return (DataSource) method.invoke(dataSource);
        } catch (Exception e) {
            log.debug("【sangsang】反射获取DataSource异常 class:{} method:{}", dataSource.getClass().getName(), methodName, e);
            return null;
        }
    }

    /**
     * 获取类及其父类中的指定方法
     *
     * @author liutangqi
     * @date 2026/3/26 17:33
     * @Param [cls, methodName]
     **/
    private static Method getMethod(Class cls, String methodName) {
        Method method = Stream.of(cls.getDeclaredMethods()).filter(f -> f.getName().equals(methodName)).findAny().orElse(null);

        Class superClass = cls.getSuperclass();
        while (method == null && superClass != null) {
            method = Stream.of(superClass.getDeclaredMethods()).filter(f -> f.getName().equals(methodName)).findAny().orElse(null);
            superClass = superClass.getSuperclass();
        }
        return method;
    }
}
