package com.sangsang.domain.annos.encryptor;

import com.sangsang.domain.strategy.encryptor.ShardingTableStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 当加密的表是进行了分表的表时，在实体类上面标注这个注解
 * 注意：这个注解必须配合@TableName一起使用
 *
 * @author liutangqi
 * @date 2024/10/11 10:45
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ShardingTableEncryptor {
    /**
     * 通过分表前的原始表名能获取到所有分表后的表名
     **/
    Class<? extends ShardingTableStrategy> value();
}
