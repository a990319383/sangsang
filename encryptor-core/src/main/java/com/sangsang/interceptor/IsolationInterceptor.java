package com.sangsang.interceptor;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.annos.FieldInterceptorOrder;
import com.sangsang.domain.annos.isolation.ForbidIsolation;
import com.sangsang.domain.constants.InterceptorOrderConstant;
import com.sangsang.domain.context.IsolationHolder;
import com.sangsang.util.InterceptorUtil;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.StringUtils;
import com.sangsang.visitor.isolation.IsolationStatementVisitor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.Properties;

/**
 * 数据权限拦截器
 *
 * @author liutangqi
 * @date 2025/6/13 13:23
 * @Param
 **/
@FieldInterceptorOrder(InterceptorOrderConstant.ISOLATION)
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
@Slf4j
public class IsolationInterceptor implements Interceptor, BeanPostProcessor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        //1.获取拦截器中提供的一些对象
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();

        //2.获取当前执行sql头上是否有@ForbidIsolation，或者当前执行的方法上下文中是否有@ForbidIsolation，直接跳过
        if (InterceptorUtil.getMapperAnnotation(statementHandler, ForbidIsolation.class) != null
                || IsolationHolder.getForbidIsolation() != null) {
            return invocation.proceed();
        }

        //3.获取当前执行的sql
        BoundSql boundSql = statementHandler.getBoundSql();
        String oldSql = boundSql.getSql();

        //4.如果当前sql的表不涉及数据权限隔离，则不处理，直接返回
        if (StringUtils.notExist(oldSql, TableCache.getIsolationTable())) {
            return invocation.proceed();
        }

        //5.将原sql进行数据隔离
        String newSql = oldSql;
        try {
            log.debug("【isolation】旧sql：{}", oldSql);
            Statement statement = JsqlparserUtil.parse(oldSql);
            IsolationStatementVisitor ilStatementVisitor = new IsolationStatementVisitor();
            statement.accept(ilStatementVisitor);
            if (StringUtils.isNotBlank(ilStatementVisitor.getResultSql())) {
                newSql = ilStatementVisitor.getResultSql();
                log.debug("【isolation】新sql：{}", newSql);
            }
        } catch (Exception e) {
            log.error("【isolation】 sql异常 原sql:{}", oldSql, e);
        }

        //6.反射修改 SQL 语句。
        Field field = boundSql.getClass().getDeclaredField("sql");
        field.setAccessible(true);
        field.set(boundSql, newSql);

        //7.执行修改后的 SQL 语句。
        return invocation.proceed();
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
            if (sessionFactory.getConfiguration().getInterceptors().stream().filter(f -> IsolationInterceptor.class.isAssignableFrom(f.getClass())).findAny().orElse(null) == null) {
                sessionFactory.getConfiguration().addInterceptor(new IsolationInterceptor());
                log.info("【isolation】手动注册拦截器 IsolationInterceptor");
            }

            //修改拦截器顺序
            InterceptorUtil.sort(sessionFactory.getConfiguration());
        }
        return bean;
    }
}