package com.sangsang.domain.dto;

import com.sangsang.domain.annos.isolation.DataIsolation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * @author liutangqi
 * @date 2024/5/17 10:59
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TableInfoDto {
    /**
     * 表名
     */
    private String tableName;

    /**
     * 该表拥有的全部字段
     */
    private Set<TableFieldDto> tableFields;

    /**
     * 表名上面标注@DataIsolation
     */
    private DataIsolation dataIsolation;
}
