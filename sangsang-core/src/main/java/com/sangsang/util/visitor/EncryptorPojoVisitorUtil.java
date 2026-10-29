package com.sangsang.util.visitor;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.visitor.pojoencrtptor.PlaceholderExpressionVisitor;
import com.sangsang.visitor.pojoencrtptor.PlaceholderSelectVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * pojo模式访问者工具类
 *
 * @author liutangqi
 * @date 2026/10/29 10:16
 */
public class EncryptorPojoVisitorUtil {

    /**
     * 解析updateSet中字段和占位符的对应关系
     *
     * @author liutangqi
     * @date 2026/10/28 16:00
     * @Param [updateSets, fieldParseTable]
     **/
    public static void updateSets(List<UpdateSet> updateSets,
                                  BaseFieldParseTable baseFieldParseTable,
                                  Map<String, ColumnTableDto> placeholderColumnTableMap) {
        if (CollectionUtils.isEmpty(updateSets)) {
            return;
        }
        for (UpdateSet updateSet : updateSets) {
            if (CollectionUtils.isEmpty(updateSet.getValues())) {
                continue;
            }
            //处理 update set(xxx,xxx) = (select xxx,xxx from) 这种子查询
            if (updateSet.getValues().size() == 1 && updateSet.getValues().get(0) instanceof ParenthesedSelect) {
                ParenthesedSelect parenthesedSelect = (ParenthesedSelect) updateSet.getValues().get(0);
                PlaceholderSelectVisitor placeholderSelectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(baseFieldParseTable, placeholderColumnTableMap, updateSet.getColumns());
                parenthesedSelect.accept(placeholderSelectVisitor);
            }
            //其它普通语法，一次将 Column和Value做对应即可
            else {
                EncryptorPojoVisitorUtil.columnsAndValues(updateSet.getColumns(), updateSet.getValues(), baseFieldParseTable, placeholderColumnTableMap);
            }
        }
    }

    /**
     * 解析字段列表和值列表中字段和占位符的对应关系
     *
     * @author liutangqi
     * @date 2026/10/29 11:15
     * @Param [columns, expressions, fieldParseTable, placeholderColumnTableMap]
     **/
    public static void columnsAndValues(List<? extends Column> columns,
                                        List<? extends Expression> expressions,
                                        BaseFieldParseTable baseFieldParseTable,
                                        Map<String, ColumnTableDto> placeholderColumnTableMap) {
        //字段列表和值列表长度必须对应
        if (CollectionUtils.isEmpty(columns) || CollectionUtils.isEmpty(expressions) || columns.size() != expressions.size()) {
            return;
        }
        for (int i = 0; i < columns.size(); i++) {
            JsqlparserUtil.parseWhereColumTable(baseFieldParseTable,
                    columns.get(i),
                    expressions.get(i),
                    placeholderColumnTableMap);
        }
    }

    /**
     * 解析条件表达式中的占位符
     *
     * @author liutangqi
     * @date 2026/10/29 11:20
     * @Param [expression, fieldParseTable, placeholderColumnTableMap]
     **/
    public static void expression(Expression expression,
                                  BaseFieldParseTable baseFieldParseTable,
                                  Map<String, ColumnTableDto> placeholderColumnTableMap) {
        if (expression == null) {
            return;
        }
        PlaceholderExpressionVisitor expressionVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(baseFieldParseTable, placeholderColumnTableMap);
        expression.accept(expressionVisitor);
    }

    /**
     * 解析join涉及的表及on条件中的占位符
     *
     * @author liutangqi
     * @date 2026/10/29 11:25
     * @Param [joins, fieldParseTable, placeholderColumnTableMap]
     **/
    public static void joins(List<Join> joins,
                             BaseFieldParseTable baseFieldParseTable,
                             Map<String, ColumnTableDto> placeholderColumnTableMap) {
        if (CollectionUtils.isEmpty(joins)) {
            return;
        }
        for (Join join : joins) {
            for (Expression onExpression : Optional.ofNullable(join.getOnExpressions()).orElse((List<Expression>) CollectionUtils.EMPTY_LIST)) {
                EncryptorPojoVisitorUtil.expression(onExpression, baseFieldParseTable, placeholderColumnTableMap);
            }
        }
    }
}
