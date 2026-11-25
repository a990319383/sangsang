package com.sangsang.util.visitor;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseExpressionVisitor;
import com.sangsang.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.WithItem;

import java.util.List;

/**
 * fieldParse visitor处理中的一些共有逻辑
 *
 * @author liutangqi
 * @date 2026/9/9 9:54
 */
public class FieldParseVisitorUtil {

    /**
     * 将selectItems 里面的字段解析到当前层的查询字段layerSelectTableFieldMap中
     *
     * @param selectItems         由output 或者returning语法返回的具体列字段
     * @param table               DML语句的主表，当出现带返回值的DML语句时，* 指代的就是这个主表的全部字段
     * @param baseFieldParseTable 核心解析结果
     * @param intoTargetTables    当前DML返回值的into的结果，当存在into时，这个sql其实是没有返回值的
     * @author liutangqi
     * @date 2026/11/18 14:28
     **/
    public static void dmlResultParseLayerSelect(List<SelectItem<?>> selectItems,
                                                 Table table,
                                                 BaseFieldParseTable baseFieldParseTable,
                                                 List<?> intoTargetTables) {
        //1.不存在DML返回值不处理 ，当前是into语法也不处理
        if (CollectionUtils.isEmpty(selectItems) || CollectionUtils.isNotEmpty(intoTargetTables)) {
            return;
        }

        //2.将* 完善成table的所有字段 （DML语句返回值的* 是指table表的全部字段，所以这里单独处理，不直接交给下游的visitor）
        List<SelectItem<?>> perfectSelectItems = JsqlparserUtil.perfectDmlResultItems(selectItems, table);

        //3.依次解析每个selectItem
        for (SelectItem<?> perfectSelectItem : perfectSelectItems) {
            Alias alias = perfectSelectItem.getAlias();
            Expression expression = perfectSelectItem.getExpression();
            FieldParseParseExpressionVisitor fpExpressionVisitor = FieldParseParseExpressionVisitor.newInstanceCurLayer(baseFieldParseTable, alias, table);
            expression.accept(fpExpressionVisitor);
        }
    }

    /**
     * 解析CTE语法 栗如： WITH x AS (SELECT * FROM tb_user)
     *
     * @author liutangqi
     * @date 2026/9/9 10:00
     * @Param [withItems, fieldParseParseTableSelectVisitor]
     **/
    public static void cte(List<WithItem> withItems, FieldParseParseTableSelectVisitor fieldParseParseTableSelectVisitor) {
        if (CollectionUtils.isEmpty(withItems)) {
            return;
        }
        for (WithItem withItem : withItems) {
            withItem.accept(fieldParseParseTableSelectVisitor);
        }
    }

    /**
     * 解析join语法中涉及的表结构信息
     * 备注：on后的内容解析来没用，所以没有处理
     *
     * @author liutangqi
     * @date 2026/9/9 15:13
     * @Param [joins, fieldParseParseTableFromItemVisitor]
     **/
    public static void joins(List<Join> joins, FieldParseParseTableFromItemVisitor fieldParseParseTableFromItemVisitor) {
        if (CollectionUtils.isEmpty(joins)) {
            return;
        }
        for (Join join : joins) {
            if (join.getRightItem() == null) {
                continue;
            }
            join.getRightItem().accept(fieldParseParseTableFromItemVisitor);
        }
    }


    /**
     * 解析using语法中涉及的表结构信息
     *
     * @author liutangqi
     * @date 2026/10/29 13:39
     * @Param [usingList, fieldParseTableFromItemVisitor]
     **/
    public static void usingList(List<Table> usingList, FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor) {
        if (CollectionUtils.isEmpty(usingList)) {
            return;
        }
        for (Table usingTable : usingList) {
            usingTable.accept(fieldParseTableFromItemVisitor);
        }
    }
}
