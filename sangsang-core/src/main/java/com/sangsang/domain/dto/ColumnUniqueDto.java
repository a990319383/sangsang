package com.sangsang.domain.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 标识字段唯一的信息
 *
 * @author liutangqi
 * @date 2025/7/18 9:24
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ColumnUniqueDto {
    /**
     * 字段所属的表的别名(from 后面接的表的别名)
     * 有别名是别名，没别名就是真实表名
     */
    private String tableAliasName;
    /**
     * 字段名
     */
    private String sourceColumn;
}
