package com.sangsang.util.visitor;

import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.dto.PlaceholderFieldParseTable;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.domain.wrapper.LayerHashMapWrapper;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import com.sangsang.visitor.pojoencrtptor.PlaceholderExpressionVisitor;
import com.sangsang.visitor.pojoencrtptor.PlaceholderSelectFromItemVisitor;
import com.sangsang.visitor.pojoencrtptor.PlaceholderSelectVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.ArrayList;
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
     * 解析INSERT类语法中目标字段和VALUES/SELECT占位符的对应关系
     *
     * @author liutangqi
     * @date 2026/11/4 20:50
     * @Param [select, table, columns, placeholderColumnTableMap]
     **/
    public static void insertSelect(Select select,
                                    Table table,
                                    List<Column> columns,
                                    Map<String, ColumnTableDto> placeholderColumnTableMap) {
        if (select == null || CollectionUtils.isEmpty(columns)) {
            return;
        }

        //1.把目标字段整理成字段解析结果，VALUES中的值才能按位置找到目标表字段
        List<FieldInfoDto> fieldInfoDtos = new ArrayList<>();
        for (Column column : columns) {
            fieldInfoDtos.add(FieldInfoDto.builder()
                    .columnName(column.getColumnName())
                    .sourceColumn(column.getColumnName())
                    .sourceTableName(table.getName())
                    .fromSourceTable(true)
                    .rowNumber(false)
                    .build());
        }
        Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap = new LayerHashMapWrapper();
        Map<String, List<FieldInfoDto>> fieldTableMap = new FieldHashMapWrapper<>();
        fieldTableMap.put(table.getName(), fieldInfoDtos);
        layerFieldTableMap.put(NumberConstant.ONE, fieldTableMap);

        //2.合并目标字段和来源查询的解析结果，再按目标字段顺序解析占位符
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer(null, layerFieldTableMap);
        select.accept(fieldParseTableSelectVisitor);
        PlaceholderSelectVisitor placeholderSelectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor, placeholderColumnTableMap, columns);
        select.accept(placeholderSelectVisitor);
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
        PlaceholderSelectFromItemVisitor placeholderSelectFromItemVisitor = PlaceholderSelectFromItemVisitor.newInstanceCurLayer(baseFieldParseTable, placeholderColumnTableMap);
        for (Join join : joins) {
            //1.解析join右表子查询中的占位符
            FromItem rightItem = join.getRightItem();
            if (rightItem != null) {
                rightItem.accept(placeholderSelectFromItemVisitor);
            }

            //2.解析join的on条件中的占位符
            if (CollectionUtils.isEmpty(join.getOnExpressions())) {
                continue;
            }
            for (Expression onExpression : join.getOnExpressions()) {
                EncryptorPojoVisitorUtil.expression(onExpression, baseFieldParseTable, placeholderColumnTableMap);
            }
        }
    }

    /**
     * 处理 CTE 语法里面的占位符信息
     *
     * @author liutangqi
     * @date 2026/10/29 17:52
     * @Param [withItemsList, placeholderSelectVisitor]
     **/
    public static void cte(List<WithItem> withItemsList, PlaceholderSelectVisitor placeholderSelectVisitor) {
        if (CollectionUtils.isEmpty(withItemsList)) {
            return;
        }
        for (WithItem withItem : withItemsList) {
            withItem.accept(placeholderSelectVisitor);
        }
    }


    /**
     * 通过上游的表达式列表和当前表达式列表，解析对应占位符的对应关系
     *
     * @author liutangqi
     * @date 2026/11/3 14:54
     * @Param [upstreamExpressionList, expressionList]
     **/
    public static void expressionList(PlaceholderFieldParseTable placeholderFieldParseTable,
                                      List<? extends Expression> upstreamExpressionList,
                                      ExpressionList<Expression> expressionList) {
        if (CollectionUtils.isEmpty(upstreamExpressionList) || CollectionUtils.isEmpty(expressionList) || upstreamExpressionList.size() != expressionList.size()) {
            return;
        }
        for (int i = 0; i < expressionList.size(); i++) {
            Expression curExp = expressionList.get(i);
            Expression upstreamExpression = upstreamExpressionList.get(i);
            JsqlparserUtil.parseWhereColumTable(placeholderFieldParseTable,
                    upstreamExpression,
                    curExp,
                    placeholderFieldParseTable.getPlaceholderColumnTableMap());
        }
    }
}
