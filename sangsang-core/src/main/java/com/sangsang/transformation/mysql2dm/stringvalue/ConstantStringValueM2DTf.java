package com.sangsang.transformation.mysql2dm.stringvalue;

import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.transformation.StringValueTransformation;
import net.sf.jsqlparser.expression.StringValue;

/**
 * 字符串常量双引号转换为单引号
 * 栗子："看腻了那片水" ===> '看腻了那片水'
 *
 * @author liutangqi
 * @date 2025/5/23 18:06
 * @Param
 **/
public class ConstantStringValueM2DTf extends StringValueTransformation {

    @Override
    public boolean needTransformation(StringValue stringValue) {
        return stringValue.getValue().contains(SymbolConstant.DOUBLE_QUOTES);
    }

    @Override
    public StringValue doTransformation(StringValue stringValue) {
        String newStringValue = stringValue.getValue().replaceAll(SymbolConstant.DOUBLE_QUOTES, SymbolConstant.SINGLE_QUOTES);
        stringValue.setValue(newStringValue);
        return stringValue;
    }
}
