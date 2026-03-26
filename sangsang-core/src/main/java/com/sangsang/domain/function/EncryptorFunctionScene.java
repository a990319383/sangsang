package com.sangsang.domain.function;

import com.sangsang.cache.encryptor.EncryptorInstanceCache;
import com.sangsang.domain.dto.ClassCacheKey;
import com.sangsang.domain.exception.FieldEncryptorException;
import net.sf.jsqlparser.expression.Expression;

/**
 * 根据每个表达式是明文还是密文存储来判断是调用加密还是解密方法的实现
 *
 * @author liutangqi
 * @date 2025/2/28 22:46
 */
public class EncryptorFunctionScene {

    /**
     * 不管上游对应字段，仅加密
     *
     * @author liutangqi
     * @date 2025/2/28 22:49
     * @Param []
     **/
    public static final EncryptorFunction defaultEncryption() {
        return (upstreamFieldEncryptor, currentFieldEncryptor, expression) -> {
            //当前字段不需要密文存储，返回原文
            if (currentFieldEncryptor == null) {
                return expression;
            }
            //当前字段需要密文存储，返回密文
            return EncryptorInstanceCache.<Expression>getInstance(currentFieldEncryptor.value()).encryption(expression);
        };
    }

    /**
     * 不管上游对应字段，仅解密
     *
     * @author liutangqi
     * @date 2025/2/28 22:50
     * @Param []
     **/
    public static final EncryptorFunction defaultDecryption() {
        return (upstreamFieldEncryptor, currentFieldEncryptor, expression) -> {
            //1.当前字段不需要密文存储，返回原文
            if (currentFieldEncryptor == null) {
                return expression;
            }
            //2.当前字段需要密文存储，返回密文
            return EncryptorInstanceCache.<Expression>getInstance(currentFieldEncryptor.value()).decryption(expression);
        };
    }

    /**
     * 上游有对应字段，这个时候就根据不同情况，对下游字段进行处理
     *
     * @author liutangqi
     * @date 2025/6/26 13:23
     * @Param []
     **/
    public static final EncryptorFunction upstreamColumn() {
        return (upstreamFieldEncryptor, currentFieldEncryptor, expression) -> {
            //1.上下游都为空，返回原文
            if (upstreamFieldEncryptor == null && currentFieldEncryptor == null) {
                return expression;
            }
            //2.上游密文，当前没有，进行加密处理(使用上游的算法)
            if (upstreamFieldEncryptor != null && currentFieldEncryptor == null) {
                return EncryptorInstanceCache.<Expression>getInstance(upstreamFieldEncryptor.value()).encryption(expression);
            }
            //3.上游没有，当前密文，进行解密处理(使用当前字段的算法)
            if (upstreamFieldEncryptor == null && currentFieldEncryptor != null) {
                return EncryptorInstanceCache.<Expression>getInstance(currentFieldEncryptor.value()).decryption(expression);
            }
            //4.上下游都为密文，仅算法一致，返回原文
            if (upstreamFieldEncryptor != null && currentFieldEncryptor != null && ClassCacheKey.classEquals(upstreamFieldEncryptor.value(), currentFieldEncryptor.value())) {
                return expression;
            }
            //5.上下游都是密文，但是算法不一致，这里需要先使用当前字段算法解密，再使用上游字段算法加密
            if (upstreamFieldEncryptor != null && currentFieldEncryptor != null && !ClassCacheKey.classEquals(upstreamFieldEncryptor.value(), currentFieldEncryptor.value())) {
                //5.1先使用当前字段算法进行解密
                Expression cleartextExp = EncryptorInstanceCache.<Expression>getInstance(currentFieldEncryptor.value()).decryption(expression);
                //5.2再使用上游字段进行加密
                return EncryptorInstanceCache.<Expression>getInstance(upstreamFieldEncryptor.value()).encryption(cleartextExp);
            }
            throw new FieldEncryptorException("未知场景");
        };
    }

}
