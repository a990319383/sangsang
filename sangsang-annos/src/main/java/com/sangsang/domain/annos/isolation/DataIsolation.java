package com.sangsang.domain.annos.isolation;

import com.sangsang.domain.enums.IsolationConditionalRelationEnum;
import com.sangsang.domain.enums.IsolationRelationEnum;
import com.sangsang.domain.strategy.DefaultStrategyBase;
import com.sangsang.domain.strategy.isolation.DataIsolationStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据隔离时需要使用的注解
 * 标注在@TableName的实体类上面
 *
 * @author liutangqi
 * @date 2025/6/13 10:06
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataIsolation {

    /**
     * 获取当前用户隔离的值的具体方法
     * 如果没有指定的话，取全局配置的值
     **/
    Class<? extends DataIsolationStrategy>[] value() default DefaultStrategyBase.BeanIsolationStrategy.class;

    /*
     * 如果这个类存在多个策略，则这些策略拼凑的条件之间的关系，默认是 and
     **/
    IsolationConditionalRelationEnum conditionalRelation() default IsolationConditionalRelationEnum.AND;
}
