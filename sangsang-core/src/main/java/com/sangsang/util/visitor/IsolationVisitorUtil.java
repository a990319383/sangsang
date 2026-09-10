package com.sangsang.util.visitor;

import com.sangsang.util.CollectionUtils;
import com.sangsang.visitor.isolation.IsolationExpressionVisitor;
import com.sangsang.visitor.isolation.IsolationFromItemVisitor;
import com.sangsang.visitor.isolation.IsolationSelectVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.List;
import java.util.Optional;

/**
 * isolation visitor处理中的一些共有逻辑
 *
 * @author liutangqi
 * @date 2026/9/9 9:30
 */
public class IsolationVisitorUtil {
    /**
     * 对CTE语法进行数据权限隔离
     * 栗如：with x as (select * from tb_user) select * from x
     *
     * @author liutangqi
     * @date 2026/9/9 10:20
     * @Param [withItems, isolationSelectVisitor]
     **/
    public static void cte(List<WithItem> withItems, IsolationSelectVisitor isolationSelectVisitor) {
        if (CollectionUtils.isEmpty(withItems)) {
            return;
        }
        for (WithItem withItem : withItems) {
            withItem.accept(isolationSelectVisitor);
        }
    }

    /**
     * 对joins语法进行数据权限隔离
     *
     * @author liutangqi
     * @date 2026/9/9 14:04
     * @Param [joins, isolationFromItemVisitor]
     **/
    public static void joins(List<Join> joins, IsolationFromItemVisitor isolationFromItemVisitor) {
        if (CollectionUtils.isEmpty(joins)) {
            return;
        }
        for (Join join : joins) {
            //针对 LATERAL 语法
            if (join.getRightItem() != null) {
                join.getRightItem().accept(isolationFromItemVisitor);
            }
            if (CollectionUtils.isEmpty(join.getOnExpressions())) {
                continue;
            }
            //针对on 后面表达式存在子查询的，比如用例的s17
            for (Expression onExpression : join.getOnExpressions()) {
                onExpression.accept(IsolationExpressionVisitor.newInstanceCurLayer(isolationFromItemVisitor));
            }
        }
    }


    /**
     * 对updateSets语法进行数据权限隔离
     *
     * @author liutangqi
     * @date 2026/9/9 16:19
     * @Param [updateSets, isolationExpressionVisitor]
     **/
    public static void updateSets(List<UpdateSet> updateSets, IsolationExpressionVisitor isolationExpressionVisitor) {
        if (CollectionUtils.isEmpty(updateSets)) {
            return;
        }
        for (UpdateSet updateSet : updateSets) {
            if (CollectionUtils.isEmpty(updateSet.getValues())) {
                continue;
            }
            for (Expression expression : updateSet.getValues()) {
                expression.accept(isolationExpressionVisitor);
            }
        }
    }

    /**
     * 对orderByElements语法进行数据权限隔离
     *
     * @author liutangqi
     * @date 2026/9/9 16:32
     * @Param [orderByElements, isolationExpressionVisitor]
     **/
    public static void orderByElements(List<OrderByElement> orderByElements, IsolationExpressionVisitor isolationExpressionVisitor) {
        if (CollectionUtils.isEmpty(orderByElements)) {
            return;
        }
        for (OrderByElement orderByElement : orderByElements) {
            if (orderByElement.getExpression() == null) {
                continue;
            }
            orderByElement.getExpression().accept(isolationExpressionVisitor);
        }
    }

    /**
     * 对groupByElement语法进行数据权限隔离
     *
     * @author liutangqi
     * @date 2026/9/9 18:17
     * @Param [groupByElement, isolationExpressionVisitor]
     **/
    public static void groupByElement(GroupByElement groupByElement, IsolationExpressionVisitor isolationExpressionVisitor) {
        if (groupByElement == null) {
            return;
        }
        if (groupByElement.getGroupByExpressionList() != null) {
            groupByElement.getGroupByExpressionList().accept(isolationExpressionVisitor);
        }
        if (CollectionUtils.isNotEmpty(groupByElement.getGroupingSets())) {
            for (ExpressionList expressionList : groupByElement.getGroupingSets()) {
                expressionList.accept(isolationExpressionVisitor);
            }
        }
    }
}
