package com.sangsang.aop.isolation;

import com.sangsang.domain.annos.isolation.ForbidIsolation;
import com.sangsang.domain.context.IsolationHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * 数据隔离切面，标记这个方法禁用数据自动隔离
 *
 * @author liutangqi
 * @date 2025/6/16 9:09
 */
@Aspect
public class IsolationAspect {

    @Around("@annotation(com.sangsang.domain.annos.isolation.ForbidIsolation)")
    public Object aroundAdvice(ProceedingJoinPoint pjp) throws Throwable {
        //1.获取头上注解
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = pjp.getTarget().getClass().getDeclaredMethod(signature.getName(), signature.getParameterTypes());
        ForbidIsolation forbidIsolation = method.getAnnotation(ForbidIsolation.class);
        try {
            //2.标记当前方法禁用数据隔离
            IsolationHolder.setForbidIsolation(forbidIsolation);
            //3.执行方法
            return pjp.proceed();
        } finally {
            //4.移除此次禁用数据隔离标记
            IsolationHolder.removeForbidIsolation();
        }
    }
}
