package com.sangsang.domain.function;

import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import net.sf.jsqlparser.expression.Expression;

/**
 * @author liutangqi
 * @date 2025/2/28 22:39
 */
@FunctionalInterface
public interface EncryptorFunction {

    /**
     * 根据上游对应字段和当前字段的@FieldEncryptor信息 返回加解密处理结果
     *
     * @return 具体表达式处理结果
     * @author liutangqi
     * @date 2025/6/26 13:14
     * @Param [upstreamFieldEncryptor上游对应字段标注的注解, currentFieldEncryptor 当前字段标注的注解,expression 当前的表达式]
     **/
    Expression dispose(FieldEncryptor upstreamFieldEncryptor, FieldEncryptor currentFieldEncryptor, Expression expression);
}
