package com.sangsang.demo.interceptor;

import com.sangsang.demo.threadlocal.SqlHolder;
import com.sangsang.domain.annos.FieldInterceptorOrder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.sql.Connection;

/**
 * SQL日志拦截器（StatementHandler 生命周期）
 * 记录修改前后的sql
 *
 * @author liutangqi && claude code && Sonnet 4.5
 * @date 2026/3/31
 */
@Slf4j
@Component
//标记此拦截器处于同生命周期的最外层
@FieldInterceptorOrder(Integer.MAX_VALUE)
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class SqlRecordInterceptor implements Interceptor, BeanPostProcessor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = statementHandler.getBoundSql();

        // proceed() 前：sangsang 内层拦截器还未执行，此处是原始 SQL
        String sqlBefore = boundSql.getSql().replaceAll("\\s+", " ").trim();
        log.debug("【demo-interceptor】sangsang处理前SQL: {}", sqlBefore);

        Object result = invocation.proceed();

        // proceed() 后：sangsang 内层拦截器已全部执行，BoundSql#sql 已被改写
        String sqlAfter = boundSql.getSql().replaceAll("\\s+", " ").trim();
        log.debug("【demo-interceptor】sangsang处理后SQL: {}", sqlAfter);

        //记录当前执行的sql
        SqlHolder.recordSql(sqlBefore, sqlAfter);

        return result;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (SqlSessionFactory.class.isAssignableFrom(bean.getClass())) {
            SqlSessionFactory sessionFactory = (SqlSessionFactory) bean;
            boolean alreadyRegistered = sessionFactory.getConfiguration().getInterceptors()
                    .stream()
                    .anyMatch(i -> SqlRecordInterceptor.class.isAssignableFrom(i.getClass()));
            if (!alreadyRegistered) {
                sessionFactory.getConfiguration().addInterceptor(new SqlRecordInterceptor());
                log.info("【sangsang】手动注册拦截器 SqlLogInterceptor");
            }
            com.sangsang.util.InterceptorUtil.sort(sessionFactory.getConfiguration());
        }
        return bean;
    }
}
