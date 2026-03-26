package com.sangsang.transformation.mysql2dm.column;

import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.domain.dto.ColumnTransformationDto;
import com.sangsang.transformation.ColumnTransformation;
import net.sf.jsqlparser.schema.Column;

/**
 * 字符串常量双引号转换为单引号
 * 栗子："看腻了那片水" ===> '看腻了那片水'
 *
 * @author liutangqi
 * @date 2025/5/21 10:17
 */
public class ConstantColumnM2DTf extends ColumnTransformation {

    @Override
    public boolean needTransformation(ColumnTransformationDto dto) {
        //当前Column属于常量，不是常量 && 包含 `
        return !dto.isTableFiled() && dto.getColumn().getColumnName().contains(SymbolConstant.DOUBLE_QUOTES);
    }

    @Override
    public ColumnTransformationDto doTransformation(ColumnTransformationDto dto) {
        Column column = dto.getColumn();
        String newColumnName = column.getColumnName().replaceAll(SymbolConstant.DOUBLE_QUOTES, SymbolConstant.SINGLE_QUOTES);
        column.setColumnName(newColumnName);
        return dto;
    }
}
