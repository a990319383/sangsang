package com.sangsang.transformation.mysql2dm.column;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.domain.dto.ColumnTransformationDto;
import com.sangsang.transformation.ColumnTransformation;
import net.sf.jsqlparser.schema.Column;

/**
 * 开启了强制将字段和表名双引号引起来的话，将字段给引起来
 *
 * @author liutangqi
 * @date 2025/10/30 10:22
 */
public class ForcedLowercaseColumnM2DTf extends ColumnTransformation {

    @Override
    public boolean needTransformation(ColumnTransformationDto dto) {
        //配置开启强制将字段和表名双引号引 && 当前Column属于真实表字段 && 不包含 ` && 不包含 "
        return TableCache.getCurConfig().getTransformation().isForcedLowercase()
                && dto.isFromSourceTable()
                && !dto.getColumn().getColumnName().contains(SymbolConstant.FLOAT)
                && !dto.getColumn().getColumnName().contains(SymbolConstant.DOUBLE_QUOTES);
    }

    @Override
    public ColumnTransformationDto doTransformation(ColumnTransformationDto dto) {
        Column column = dto.getColumn();
        column.setColumnName(SymbolConstant.DOUBLE_QUOTES + column.getColumnName().toLowerCase() + SymbolConstant.DOUBLE_QUOTES);
        return dto;
    }
}
