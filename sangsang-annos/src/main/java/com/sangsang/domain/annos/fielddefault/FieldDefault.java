package com.sangsang.domain.annos.fielddefault;

import com.sangsang.domain.strategy.fielddefault.FieldDefaultStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据库的实体类上面标注了这个字段，会在新增或者修改的时候设置对应的默认值
 *
 * @author liutangqi
 * @date 2025/7/9 13:45
 * @Param
 **/
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldDefault {

    /**
     * 获取默认值的策略
     *
     * @author liutangqi
     * @date 2025/7/9 14:35
     * @Param []
     **/
    Class<? extends FieldDefaultStrategy> value();

    /**
     * 是否强制覆盖sql的值
     * 如果开启此选项，不论sql中的此字段是否有维护值，都会根据上面 FieldDefaultStrategy 策略的值进行覆盖
     */
    boolean mandatoryOverride() default false;
}
