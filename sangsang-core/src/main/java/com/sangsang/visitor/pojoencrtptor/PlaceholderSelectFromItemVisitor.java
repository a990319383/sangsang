package com.sangsang.visitor.pojoencrtptor;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.PlaceholderFieldParseTable;
import com.sangsang.util.visitor.EncryptorPojoVisitorUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    /**
     * 获取当前层实例
     *
     * @author liutangqi
     * @date 2026/11/12 14:30
     * @Param [baseFieldParseTable, placeholderColumnTableMap]
     **/
    public static PlaceholderSelectFromItemVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable,
                                                                        Map<String, ColumnTableDto> placeholderColumnTableMap) {
        return new PlaceholderSelectFromItemVisitor(baseFieldParseTable, placeholderColumnTableMap, null);
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
        Optional.ofNullable(subSelect.getSelect())
                .ifPresent(p -> p.accept(PlaceholderSelectVisitor.newInstanceNextLayer(this, this.upstreamExpressionList)));
    }


    @Override
    public void visit(LateralSubSelect lateralSubSelect) {
        if (lateralSubSelect.getSelect() == null) {
            return;
        }
        //LATERAL子查询可以访问外部作用域，但解析结果不能污染外部作用域，所以单独解析占位符
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceIndividualMap(this);
        lateralSubSelect.getSelect().accept(fieldParseTableSelectVisitor);
        lateralSubSelect.getSelect().accept(PlaceholderSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor, this.getPlaceholderColumnTableMap()));

    }


    @Override
    public void visit(TableFunction tableFunction) {

    }

    @Override
    public void visit(ParenthesedFromItem aThis) {
        Optional.ofNullable(aThis.getFromItem()).ifPresent(p -> p.accept(this));

        //处理括号里面的joins
        EncryptorPojoVisitorUtil.joins(aThis.getJoins(), this, this.getPlaceholderColumnTableMap());

    }
}
