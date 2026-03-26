package com.sangsang.encryptor.db;

import com.sangsang.config.properties.SangSangProperties;
import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.domain.strategy.encryptor.FieldEncryptorStrategy;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;


/**
 * 数据库函数加密模式下默认的加解密算法
 *
 * @author liutangqi
 * @date 2024/4/8 14:13
 */
public class DefaultDBFieldEncryptorPattern implements FieldEncryptorStrategy<Expression> {

    //加密秘钥
    private SangSangProperties sangSangProperties;

    public DefaultDBFieldEncryptorPattern(SangSangProperties sangSangProperties) {
        this.sangSangProperties = sangSangProperties;
    }

    @Override
    public Expression encryption(Expression oldExpression) {
        Function aesEncryptFunction = new Function();
        aesEncryptFunction.setName(SymbolConstant.AES_ENCRYPT);
        aesEncryptFunction.setParameters(new ExpressionList(oldExpression, new StringValue(sangSangProperties.getEncryptor().getSecretKey())));
        Function toBase64Function = new Function();
        toBase64Function.setName(SymbolConstant.TO_BASE64);
        toBase64Function.setParameters(new ExpressionList(aesEncryptFunction));
        return toBase64Function;
    }

    @Override
    public Expression decryption(Expression oldExpression) {
        Function base64Function = new Function();
        base64Function.setName(SymbolConstant.FROM_BASE64);
        base64Function.setParameters(new ExpressionList(oldExpression));
        Function decryptFunction = new Function();
        decryptFunction.setName(SymbolConstant.AES_DECRYPT);
        decryptFunction.setParameters(new ExpressionList(base64Function, new StringValue(sangSangProperties.getEncryptor().getSecretKey())));

        //类型转换，避免上面解密函数出现中文乱码
        CastExpression castExpression = new CastExpression();
        castExpression.setLeftExpression(decryptFunction);
        castExpression.setColDataType(SymbolConstant.COLDATATYPE_HCAR);
        return castExpression;
    }
}
