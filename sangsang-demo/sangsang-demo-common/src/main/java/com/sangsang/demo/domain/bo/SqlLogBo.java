package com.sangsang.demo.domain.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author liutangqi
 * @date 2026/4/2 13:09
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlLogBo {
    /**
     * sangsang处理前的原始SQL
     */
    private String before;

    /**
     * sangsang处理后的SQL
     */
    private String after;
}
