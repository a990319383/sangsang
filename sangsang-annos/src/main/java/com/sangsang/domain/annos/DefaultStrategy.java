package com.sangsang.domain.annos;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 实现的策略如果有多种实现，标注了这个的被视为是默认实现
 * 栗子：字段加密功能的项目有多种加解密策略，当不手动指定加解密策略时，则使用标注了这个的默认策略
 * 备注：db模式，pojo模式的加解密实现策略，数据隔离策略都适配于这个注解
 *
 * @author liutangqi
 * @date 2025/6/23 14:27
 * @Param
 **/
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DefaultStrategy {
}
