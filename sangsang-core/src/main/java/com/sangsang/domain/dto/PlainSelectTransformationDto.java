package com.sangsang.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * @author liutangqi
 * @date 2026/1/5 14:38
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlainSelectTransformationDto {
    /**
     * 当前的select语句
     */
    private PlainSelect plainSelect;

    /**
     * 当前sql的解析结果
     * 注意：如果要改这个里面解析结果的值将会影响整条sql的解析结果，这个结果集是整个sql解析生命周期共用的，请根据实际情况看是否能修改里面的值
     */
    private BaseFieldParseTable baseFieldParseTable;
}
