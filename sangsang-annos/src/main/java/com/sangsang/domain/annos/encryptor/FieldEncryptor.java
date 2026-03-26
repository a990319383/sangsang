package com.sangsang.domain.annos.encryptor;

import com.sangsang.domain.strategy.DefaultStrategyBase;
import com.sangsang.domain.strategy.encryptor.FieldEncryptorStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据库实体类上标注这个注解，标识这个字段的查询会解密 修改插入会加密
 *
 * @author liutangqi
 * @date 2024/2/1 13:40
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldEncryptor {
    /**
     * 默认的加解密策略
     */
    Class<? extends FieldEncryptorStrategy> value() default DefaultStrategyBase.EncryptorBeanStrategy.class;
}
