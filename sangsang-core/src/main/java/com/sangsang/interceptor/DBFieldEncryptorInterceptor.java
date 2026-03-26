package com.sangsang.interceptor;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.annos.FieldInterceptorOrder;
import com.sangsang.domain.constants.InterceptorOrderConstant;
import com.sangsang.util.InterceptorUtil;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.StringUtils;
import com.sangsang.visitor.dbencrtptor.DBDencryptStatementVisitor;
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
 * 采用数据库函数加解密模式
 * 将sql需要加解密的字段进行加解密处理
 *
 * @author liutangqi
 * @date 2023/11/9 19:03
 */
@FieldInterceptorOrder(InterceptorOrderConstant.ENCRYPTOR)
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
@Slf4j
public class DBFieldEncryptorInterceptor implements Interceptor, BeanPostProcessor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        //1.获取拦截器中提供的一些对象
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();

        //2.获取当前执行的sql
        BoundSql boundSql = statementHandler.getBoundSql();
        String oldSql = boundSql.getSql();
        log.debug("【sangsang】<encryptor>旧sql：{}", oldSql);

        //3.当前sql如果肯定不需要加解密，则不解析sql，直接返回
        if (StringUtils.notExist(oldSql, TableCache.getFieldEncryptTable())) {
            return invocation.proceed();
        }

        //4.将原sql进行加解密处理
        String newSql = oldSql;
        try {
            Statement statement = JsqlparserUtil.parse(oldSql);
            DBDencryptStatementVisitor dbStatementVisitor = new DBDencryptStatementVisitor();
            statement.accept(dbStatementVisitor);
            if (StringUtils.isNotBlank(dbStatementVisitor.getResultSql())) {
                newSql = dbStatementVisitor.getResultSql();
                log.debug("【sangsang】<encryptor>新sql：{}", newSql);
            }
        } catch (Exception e) {
            log.error("【sangsang】<encryptor>加解密sql异常 原sql:{}", oldSql, e);
        }

        //5.反射修改 SQL 语句。
        Field field = boundSql.getClass().getDeclaredField("sql");
        field.setAccessible(true);
        field.set(boundSql, newSql);

        //6.执行修改后的 SQL 语句。
        return invocation.proceed();
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
                    .filter(f -> DBFieldEncryptorInterceptor.class.isAssignableFrom(f.getClass()))
                    .findAny()
                    .orElse(null) == null) {
                sessionFactory.getConfiguration().addInterceptor(new DBFieldEncryptorInterceptor());
                log.info("【sangsang】手动注册拦截器 DBFieldEncryptorInterceptor");
            }

            //修改拦截器顺序
            InterceptorUtil.sort(sessionFactory.getConfiguration());
        }
        return bean;
    }


}
