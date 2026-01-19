package com.sangsang.interceptor;

import com.sangsang.cache.encryptor.EncryptorInstanceCache;
import com.sangsang.cache.transformation.TransformationSqlCache;
import com.sangsang.domain.annos.FieldInterceptorOrder;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.constants.FieldConstant;
import com.sangsang.domain.constants.InterceptorOrderConstant;
import com.sangsang.domain.context.TfParameterMappingHolder;
import com.sangsang.domain.context.TransformationHolder;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.InterceptorUtil;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.StringUtils;
import com.sangsang.visitor.transformation.TransformationStatementVisitor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * 进行sql语法转换的拦截器
 *
 * @author liutangqi
 * @date 2025/5/21 10:28
 */
@FieldInterceptorOrder(InterceptorOrderConstant.TRANSFORMATION)
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
@Slf4j
public class TransformationInterceptor implements Interceptor, BeanPostProcessor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        //1.获取拦截器中提供的一些对象
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();

        //2.获取当前执行的sql
        BoundSql boundSql = statementHandler.getBoundSql();
        String oldSql = boundSql.getSql();

        //3.如果当前sql肯定不需要语法转换，则直接执行
        if (TransformationSqlCache.isNeedlessTransformationSql(oldSql)) {
            return invocation.proceed();
        }

        String newSql = oldSql;
        List<ParameterMapping> parameterMapping = null;
        try {
            log.debug("【db-transformation】旧sql：{}", oldSql);
            //4. 将所有的 ? 占位符替换为 SymbolConstant.PLACEHOLDER+自增序列的格式，方便语法解析器中获取具体值
            String placeholderSql = StringUtils.question2Placeholder(oldSql);

            //5. 将当前每个参数对应的入参值维护到 TfParameterMappingHolder中
            recordParameterMapping(boundSql);

            //6. 走语法转换的逻辑
            Statement statement = JsqlparserUtil.parse(placeholderSql);
            TransformationStatementVisitor transformationStatementVisitor = new TransformationStatementVisitor();
            statement.accept(transformationStatementVisitor);
            if (StringUtils.isNotBlank(transformationStatementVisitor.getResultSql())) {
                //7. 将占位符 SymbolConstant.PLACEHOLDER+自增序列 替换为 ?
                newSql = StringUtils.placeholder2Question(transformationStatementVisitor.getResultSql());
                log.debug("【db-transformation】新sql：{}", newSql);
            }

            //8. 根据当前sql解析器是否移除了#{}参数值，获取当前最新的参数映射值
            parameterMapping = curParameterMapping(boundSql);

            //9. 如果当前sql语句未发生了语法转换，则将当前sql放入缓存中，避免下次重复处理
            if (!TransformationHolder.isTransformation()) {
                TransformationSqlCache.addNeedlessTransformationSql(oldSql);
            }
        } catch (Exception e) {
            log.error("【db-transformation】语法转换 sql异常 原sql:{}", oldSql, e);
        } finally {
            //10. 移除掉此逻辑中会使用到的ThreadLocal
            clearThreadLocal(oldSql);
        }

        //11.反射修改 SQL 语句和入参
        MetaObject metaObject = InterceptorUtil.forObject(boundSql);
        metaObject.setValue("parameterMappings", parameterMapping);
        metaObject.setValue("sql", newSql);

        //12.执行修改后的 SQL 语句。
        return invocation.proceed();
    }


    /**
     * finally中移除掉此逻辑中会使用到的ThreadLocal
     *
     * @author liutangqi
     * @date 2026/1/9 14:23
     * @Param [oldSql]
     **/
    private void clearThreadLocal(String oldSql) {
        //手动清除，避免内存泄漏
        try {
            TransformationHolder.clear();
        } catch (Exception e) {
            log.error("【db-transformation】移除TransformationHolder异常 原sql:{}", oldSql, e);
        }
        //手动清除，避免内存泄漏
        try {
            TfParameterMappingHolder.clearParameterMapping();
        } catch (Exception e) {
            log.error("【db-transformation】clearParameterMapping异常 原sql:{}", oldSql, e);
        }
        //手动清除，避免内存泄漏
        try {
            TfParameterMappingHolder.clearRemoveParameterMapping();
        } catch (Exception e) {
            log.error("【db-transformation】clearRemoveParameterMapping异常 原sql:{}", oldSql, e);
        }
    }

    /**
     * 根据当前语法解析器的执行情况，将原先sql中有但是语法转换后不存在了的#{}给移除掉，返回一个最新的
     * 注意：这里必须返回一个新的List，不能复用之前的List，复用会影响到其它拦截器
     *
     * @author liutangqi
     * @date 2026/1/9 14:13
     * @Param []
     **/
    private List<ParameterMapping> curParameterMapping(BoundSql boundSql) {
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        Set<Integer> removeIndex = TfParameterMappingHolder.getRemoveParameterMapping();
        //当前不存在移除的#{}，返回原本的数据
        if (CollectionUtils.isEmpty(removeIndex)) {
            return parameterMappings;
        }
        //存在移除了的#{}，则构建一个新list返回最新的
        List<ParameterMapping> curParameterMappings = new ArrayList<>();
        for (int i = 0; i < parameterMappings.size(); i++) {
            if (!removeIndex.contains(i)) {
                curParameterMappings.add(parameterMappings.get(i));
            }
        }
        return curParameterMappings;
    }

    /**
     * 将当前sql的 #{}的每个占位符对应的具体值维护到 TfParameterMappingHolder 中
     *
     * @author liutangqi
     * @date 2026/1/9 14:00
     * @Param [boundSql]
     **/
    private void recordParameterMapping(BoundSql boundSql) {
        //1.获取所有入参（这个的顺序和占位符顺序一致）
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        //2.循环所有的入参，将每个入参的值记录到ThreadLocal中，其中key是FieldConstant.PLACEHOLDER+自增序列
        for (int i = 0; i < parameterMappings.size(); i++) {
            ParameterMapping parameterMapping = parameterMappings.get(i);
            //sql关系中，占位符被统一替换成了这个
            String placeholderKey = FieldConstant.PLACEHOLDER + i;
            //获取当前映射字段的入参值
            Object propertyValue = InterceptorUtil.parseObj(boundSql, parameterMapping);
            //记录到ThreadLocal中
            TfParameterMappingHolder.setParameterMapping(placeholderKey, propertyValue);
        }
    }


    /**
     * 低版本mybatis 这个方法不是default 方法，会报错找不到实现方法，所以这里实现默认的方法
     *
     * @author liutangqi
     * @date 2025/5/21 10:28
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
     * @date 2025/5/21 10:28
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
     * @date 2025/5/21 10:28
     * @Param [bean, beanName]
     **/
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        //当前没有注册此拦截器，则手动注册，避免有些项目自定义了SqlSessionFactory 导致拦截器漏注册
        //使用@Bean的方式注册，可能会导致某些项目的@PostContruct先于拦截器执行，导致拦截器业务代码失效
        if (SqlSessionFactory.class.isAssignableFrom(bean.getClass())) {
            SqlSessionFactory sessionFactory = (SqlSessionFactory) bean;
            if (sessionFactory.getConfiguration().getInterceptors().stream().filter(f -> TransformationInterceptor.class.isAssignableFrom(f.getClass())).findAny().orElse(null) == null) {
                sessionFactory.getConfiguration().addInterceptor(new TransformationInterceptor());
                log.info("【db-transformation】手动注册拦截器 TransformationInterceptor");
            }

            //修改拦截器顺序
            InterceptorUtil.sort(sessionFactory.getConfiguration());
        }
        return bean;
    }
}