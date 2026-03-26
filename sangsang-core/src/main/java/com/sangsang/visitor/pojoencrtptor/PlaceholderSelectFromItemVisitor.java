package com.sangsang.visitor.pojoencrtptor;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.PlaceholderFieldParseTable;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;

import java.util.List;
import java.util.Map;

/**
 * 解析from 后面的 #{}占位符
 *
 * @author liutangqi
 * @date 2024/7/12 17:04
 */
public class PlaceholderSelectFromItemVisitor extends PlaceholderFieldParseTable implements FromItemVisitor {

    /**
     * 需要和上游关联时的上游的表达式
     * 例如： ? in (select xxx from table) 这种场景，这里存储的就是上游的表达式
     **/
    private List<? extends Expression> upstreamExpressionList;


    private PlaceholderSelectFromItemVisitor(BaseFieldParseTable baseFieldParseTable,
                                             Map<String, ColumnTableDto> placeholderColumnTableMap,
                                             List<? extends Expression> upstreamExpressionList) {
        super(baseFieldParseTable, placeholderColumnTableMap);
        this.upstreamExpressionList = upstreamExpressionList;
    }

    /**
     * 获取当前层实例
     *
     * @author liutangqi
     * @date 2025/12/23 10:29
     * @Param [placeholderFieldParseTable, upstreamExpressionList]
     **/
    public static PlaceholderSelectFromItemVisitor newInstanceCurLayer(PlaceholderFieldParseTable placeholderFieldParseTable, List<? extends Expression> upstreamExpressionList) {
        return new PlaceholderSelectFromItemVisitor(placeholderFieldParseTable, placeholderFieldParseTable.getPlaceholderColumnTableMap(), upstreamExpressionList);
    }

    /**
     * 获取当前层实例
     *
     * @author liutangqi
     * @date 2025/12/23 10:29
     * @Param [placeholderFieldParseTable, upstreamExpressionList]
     **/
    public static PlaceholderSelectFromItemVisitor newInstanceCurLayer(PlaceholderFieldParseTable placeholderFieldParseTable) {
        return new PlaceholderSelectFromItemVisitor(placeholderFieldParseTable, placeholderFieldParseTable.getPlaceholderColumnTableMap(), null);
    }

    @Override
    public void visit(Table table) {
    }


    /**
     * 子查询
     *
     * @author liutangqi
     * @date 2024/7/12 17:09
     * @Param [subSelect]
     **/
    @Override
    public void visit(ParenthesedSelect subSelect) {
        PlaceholderSelectVisitor placeholderSelectVisitor = PlaceholderSelectVisitor.newInstanceNextLayer(this, this.upstreamExpressionList);
        subSelect.getSelect().accept(placeholderSelectVisitor);
    }


    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }


    @Override
    public void visit(TableFunction tableFunction) {

    }

    @Override
    public void visit(ParenthesedFromItem aThis) {

    }
}
