package com.sangsang.transformation.mysql2dm.alias;

import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.transformation.AliasTransformation;
import com.sangsang.util.StringUtils;
import net.sf.jsqlparser.expression.Alias;

/**
 * 别名的 `  ' 转 "
 * 栗子：select user_name as `um`  ===> select user_name as "um"
 *
 * @author liutangqi
 * @date 2025/6/4 18:19
 */
public class QuotationMarkAliasM2DTf extends AliasTransformation {
    @Override
    public boolean needTransformation(Alias alias) {
        return alias.getName().contains(SymbolConstant.FLOAT) || alias.getName().contains(SymbolConstant.SINGLE_QUOTES);
    }

    @Override
    public Alias doTransformation(Alias alias) {
        String name = alias.getName();
        name = StringUtils.trimSymbol(name, SymbolConstant.FLOAT);
        name = StringUtils.trimSymbol(name, SymbolConstant.SINGLE_QUOTES);
        alias.setName(SymbolConstant.DOUBLE_QUOTES + name + SymbolConstant.DOUBLE_QUOTES);
        return alias;
    }
}
