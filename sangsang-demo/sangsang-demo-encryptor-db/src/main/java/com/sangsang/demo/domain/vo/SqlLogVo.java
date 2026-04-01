package com.sangsang.demo.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条SQL改写前后对比
 *
 * @author liutangqi
 * @date 2026/3/31
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SqlLogVo {
    /**
     * sangsang处理前的原始SQL
     */
    private String before;

    /**
     * sangsang处理后的SQL
     */
    private String after;
}
