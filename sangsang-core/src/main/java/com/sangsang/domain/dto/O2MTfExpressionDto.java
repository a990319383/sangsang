package com.sangsang.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * oracle转mysql分页处理时对Expression表达式处理后的结果集
 *
 * @author liutangqi
 * @date 2026/1/6 18:55
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class O2MTfExpressionDto {
    /**
     * 是否需要保留当前表达式
     * 默认要保留
     */
    @Builder.Default
    private boolean retainExpression = true;

    /**
     * 分页时的 行号rowNumber >= 的值
     */
    private Long ge;

    /**
     * 分页时的 行号rowNumber <= 的值
     */
    private Long le;


    public static final O2MTfExpressionDto DEFAULT = O2MTfExpressionDto.builder().build();
    public static final O2MTfExpressionDto NOT_RETAIN = O2MTfExpressionDto.builder().retainExpression(false).build();
}
