package com.sangsang.domain.strategy.desensitize;

/**
 * @author liutangqi
 * @date 2025/4/7 17:19
 */
@FunctionalInterface
public interface DesensitizeStrategy<T> {

    /**
     * 执行脱敏的具体逻辑
     * 备注：有些场景下即使字段是null也需要脱敏处理，所以这里逻辑全部由用户自己实现
     * 注意：cleartext 字段可能为null，请注意处理，避免空指针！！！
     *
     * @author liutangqi
     * @date 2025/4/7 17:20
     * @Param [cleartext:具体的字段的明文，t:当前一整个对象]
     **/
    String desensitize(String cleartext, T t);
}
