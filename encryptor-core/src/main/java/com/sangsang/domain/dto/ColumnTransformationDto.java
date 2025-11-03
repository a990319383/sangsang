package com.sangsang.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.sf.jsqlparser.schema.Column;

/**
 * @author liutangqi
 * @date 2025/5/23 9:51
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ColumnTransformationDto {
    /**
     * 当前具体的字段
     */
    private Column column;

    /**
     * 是否是表字段 (这个表可能是虚拟表，属于虚拟表的字段这个值也会判断属于表字段)
     * true: 当前Column属于表字段
     * false: 当前Column不属于表字段，属于常量
     */
    private boolean tableFiled;

    /**
     * 这个字段是否属于真实表字段
     * true:这个字段直接属于某个真实的表字段，属于虚拟的临时表的不算
     * false:不属于
     **/
    private boolean fromSourceTable;
}
