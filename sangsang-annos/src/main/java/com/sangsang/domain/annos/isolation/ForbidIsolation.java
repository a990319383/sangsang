package com.sangsang.domain.annos.isolation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标识此方法不需要数据隔离
 * 使用方法：标注在mapper上，或者是service上
 *
 * @author liutangqi
 * @date 2025/6/13 18:21
 * @Param
 **/
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ForbidIsolation {
}
