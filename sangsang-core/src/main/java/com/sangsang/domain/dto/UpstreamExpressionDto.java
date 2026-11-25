package com.sangsang.domain.dto;

import lombok.Data;
import net.sf.jsqlparser.expression.Expression;

/**
 * 用于记录上游对应字段的信息
 *
 * @author liutangqi
 * @date 2026/11/20 10:12
 */
@Data
public class UpstreamExpressionDto extends ColumnTableDto {
    /**
     * 上游对应的表达式
     * 注意：这里上游对应是有可能是上游作用域，也有可能是当前层作用域的字段，仅仅表示两者有对应关系的
     * 所以这里一般用的时候，就先解析这个字段的所属表信息，下游使用的时候再解析的话，不知道这个字段是哪层作用域里面的
     */
    private Expression upstreamExpression;


    private UpstreamExpressionDto() {
    }

    private UpstreamExpressionDto(Expression upstreamExpression) {
        this.upstreamExpression = upstreamExpression;
    }

    public static UpstreamExpressionDto newInstance(Expression upstreamExpression) {
        return new UpstreamExpressionDto(upstreamExpression);
    }

    public static UpstreamExpressionDto newInstance(Expression upstreamExpression, ColumnTableDto columnTableDto) {
        UpstreamExpressionDto instance = new UpstreamExpressionDto(upstreamExpression);
        instance.setTableAliasName(columnTableDto.getTableAliasName());
        instance.setSourceTableName(columnTableDto.getSourceTableName());
        instance.setSourceColumn(columnTableDto.getSourceColumn());
        instance.setFromSourceTable(columnTableDto.isFromSourceTable());
        instance.setRowNumber(columnTableDto.isRowNumber());
        return instance;
    }

}
