package com.sangsang.domain.strategy.encryptor;


/**
 * 加解密策略，想要实现自定义的加解密策略的话，实现这个接口
 * 注意：目前T 仅支持 String 和 Expression类型
 *
 * @author liutangqi
 * @date 2025/6/30 17:42
 */
public interface FieldEncryptorStrategy<T> {
    
    /**
     * 加密算法
     *
     * @author liutangqi
     * @date 2024/4/8 14:12
     * @Param [oldExpression]
     **/
    T encryption(T oldExpression);

    /**
     * 解密算法
     *
     * @author liutangqi
     * @date 2024/4/8 14:13
     * @Param [oldExpression]
     **/
    T decryption(T oldExpression);
}
