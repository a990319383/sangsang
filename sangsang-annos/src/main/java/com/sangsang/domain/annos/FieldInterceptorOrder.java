package com.sangsang.domain.annos;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 控制自己写的拦截器的顺序
 * 只有标记了这个注解的拦截器才会手动调整顺序，没有标记的，按照默认顺序走
 *
 * @author liutangqi
 * @date 2025/5/27 9:11
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldInterceptorOrder {
    /**
     * 同生命周期的拦截器，序号越小越先获取到结果
     * 注意：这里的执行是指先获取到结果，拦截器执行是嵌套的，先执行的拦截器，可能是后拿到invocation.proceed()的结果的
     */
    int value();
}
