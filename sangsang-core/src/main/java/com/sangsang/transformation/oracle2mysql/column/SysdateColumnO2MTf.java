package com.sangsang.transformation.oracle2mysql.column;

import com.sangsang.domain.dto.ColumnTransformationDto;
import com.sangsang.transformation.ColumnTransformation;

/**
 * JSqlParser 在部分场景下会把 SYSDATE 解析成 Column，
 * 这里兜底转换为 NOW()。
 *
 * @author liutangqi && codex && gpt-5.4
 * @date 2026/3/24 18:30
 */
public class SysdateColumnO2MTf extends ColumnTransformation {
    @Override
    public boolean needTransformation(ColumnTransformationDto dto) {
        return !dto.isTableFiled()
                && dto.getColumn() != null
                && "SYSDATE".equalsIgnoreCase(dto.getColumn().getColumnName());
    }

    @Override
    public ColumnTransformationDto doTransformation(ColumnTransformationDto dto) {
        dto.getColumn().setColumnName("NOW()");
        return dto;
    }
}
