package com.sangsang.util.visitor;

import com.sangsang.cache.transformation.TransformationInstanceCache;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.ColumnTransformationDto;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.StringUtils;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import com.sangsang.visitor.transformation.TransformationExpressionVisitor;
import com.sangsang.visitor.transformation.TransformationSelectVisitor;
import com.sangsang.visitor.transformation.wrap.ExpressionWrapper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 语法转换visitor处理中的一些共有逻辑
 *
 * @author liutangqi
 * @date 2026/11/4 20:40
 */
public class TransformationVisitorUtil {

    /**
     * 判断当前column 是否是表字段还是常量
     * 主要用于语法转换时 ` " ‘ 符号的取舍
     * 当这个方法返回的是true时，说明这个字段不是常量，可能来自某张虚拟表或者真实表的字段
     * 注意：来自虚拟表的字段，哪怕这个字段在原虚拟表中属于常量，这个也是表字段
     *
     * @author liutangqi
     * @date 2025/5/27 13:08
     * @Param [column, layer, layerFieldTableMap]
     **/
    public static boolean isTableFiled(Column column, int layer, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        //字段名
        String columName = column.getColumnName();
        //字段所属表 （只有select 别名.字段名 时这个才有值，其它的为null）
        Table table = column.getTable();

        //1.没有指定表名时，从当前层的表的所有字段里面找到这个名字的表( select 字段)
        if (table == null) {
            for (Map.Entry<String, List<FieldInfoDto>> entry : layerFieldTableMap.getOrDefault(layer, new FieldHashMapWrapper<>()).entrySet()) {
                //任意的表有一个字段符合，则返回true，表示是个表字段
                if (entry.getValue().stream().anyMatch(m -> StringUtils.fieldEquals(m.getColumnName(), columName))) {
                    return true;
                }
            }
        }

        //2.有指定表名时，说明肯定来自某张表，哪怕是虚拟表
        return table != null;
    }

    /**
     * 对表达式进行语法转换
     *
     * @author liutangqi
     * @date 2026/11/4 20:40
     * @Param [expression, baseFieldParseTable]
     **/
    public static Expression expression(Expression expression, BaseFieldParseTable baseFieldParseTable) {
        if (expression == null) {
            return null;
        }
        TransformationExpressionVisitor expressionVisitor = TransformationExpressionVisitor.newInstanceCurLayer(baseFieldParseTable);
        Expression tfExpression = ExpressionWrapper.wrap(expression).accept(expressionVisitor);
        return Optional.ofNullable(tfExpression).orElse(expression);
    }

    /**
     * 对数据库字段列表进行语法转换
     *
     * @author liutangqi
     * @date 2026/11/4 20:40
     * @Param [columns]
     **/
    public static void columns(List<Column> columns) {
        if (CollectionUtils.isEmpty(columns)) {
            return;
        }
        for (int i = 0; i < columns.size(); i++) {
            ColumnTransformationDto columnTransformationDto = new ColumnTransformationDto(columns.get(i), true, true);
            ColumnTransformationDto transformation = TransformationInstanceCache.transformation(columnTransformationDto);
            if (transformation != null) {
                columns.set(i, transformation.getColumn());
            }
        }
    }

    /**
     * 对字段和值一一对应的语法分支进行转换
     *
     * @author liutangqi
     * @date 2026/11/4 20:40
     * @Param [columns, values, baseFieldParseTable]
     **/
    public static void columnsAndValues(ExpressionList<Column> columns,
                                        ExpressionList<Expression> values,
                                        BaseFieldParseTable baseFieldParseTable) {
        if (CollectionUtils.isEmpty(columns) || CollectionUtils.isEmpty(values) || columns.size() != values.size()) {
            return;
        }
        TransformationVisitorUtil.columns(columns);
        for (int i = 0; i < values.size(); i++) {
            values.set(i, TransformationVisitorUtil.expression(values.get(i), baseFieldParseTable));
        }
    }

    /**
     * 对updateSets语法中的字段和值进行转换
     *
     * @author liutangqi
     * @date 2026/11/4 20:40
     * @Param [updateSets, baseFieldParseTable]
     **/
    public static void updateSets(List<UpdateSet> updateSets, BaseFieldParseTable baseFieldParseTable) {
        if (CollectionUtils.isEmpty(updateSets)) {
            return;
        }
        for (UpdateSet updateSet : updateSets) {
            TransformationVisitorUtil.columns(updateSet.getColumns());
            if (CollectionUtils.isEmpty(updateSet.getValues())) {
                continue;
            }
            ExpressionList<Expression> values = (ExpressionList<Expression>) updateSet.getValues();
            for (int i = 0; i < values.size(); i++) {
                values.set(i, TransformationVisitorUtil.expression(values.get(i), baseFieldParseTable));
            }
        }
    }

    /**
     * 对独立的SELECT语句进行语法转换
     *
     * @author liutangqi
     * @date 2026/11/4 20:40
     * @Param [select]
     **/
    public static Select select(Select select) {
        if (select == null) {
            return null;
        }
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
        select.accept(fieldParseTableSelectVisitor);
        TransformationSelectVisitor transformationSelectVisitor = TransformationSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor);
        select.accept(transformationSelectVisitor);
        Select processedSelect = transformationSelectVisitor.getProcessedPlainSelect();
        return Optional.ofNullable(processedSelect).orElse(select);
    }

    /**
     * 对CTE内部的查询进行语法转换
     *
     * @author liutangqi
     * @date 2026/11/4 20:40
     * @Param [withItems]
     **/
    public static void cte(List<WithItem> withItems) {
        if (CollectionUtils.isEmpty(withItems)) {
            return;
        }
        for (WithItem withItem : withItems) {
            withItem.setSelect(TransformationVisitorUtil.select(withItem.getSelect()));
        }
    }
}
