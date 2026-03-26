package com.sangsang.transformation.oracle2mysql.column;

import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.domain.dto.ColumnTransformationDto;
import com.sangsang.transformation.ColumnTransformation;
import net.sf.jsqlparser.schema.Column;

/**
 * 字段包含 双引号的转换为`
 * 栗子："name"  ===>  `name`
 *
 * @author liutangqi
 * @date 2025/5/21 10:17
 */
public class QuotationMarkColumnO2MTf extends ColumnTransformation {

    @Override
    public boolean needTransformation(ColumnTransformationDto dto) {
        //当前Column属于表字段，不是常量 && 包含 "
        return dto.isTableFiled() && dto.getColumn().getColumnName().contains(SymbolConstant.DOUBLE_QUOTES);
    }

    @Override
    public ColumnTransformationDto doTransformation(ColumnTransformationDto dto) {
        Column column = dto.getColumn();
        String newColumnName = column.getColumnName().replaceAll(SymbolConstant.DOUBLE_QUOTES, SymbolConstant.FLOAT);
        column.setColumnName(newColumnName);
        return dto;
    }
}
