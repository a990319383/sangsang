package com.sangsang.visitor.transformation;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.util.CollectionUtils;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import com.sangsang.visitor.transformation.wrap.ExpressionWrapper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;

/**
 * @author liutangqi
 * @date 2025/5/21 15:07
 */
public class TransformationSelectVisitor extends BaseFieldParseTable implements SelectVisitor {

    private TransformationSelectVisitor(int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    /**
     * 获取当前层的实例
     *
     * @author liutangqi
     * @date 2025/5/27 10:57
     * @Param [baseFieldParseTable]
     **/
    public static TransformationSelectVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new TransformationSelectVisitor(
                baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap()
        );
    }

    /**
     * 获取下层实例
     *
     * @author liutangqi
     * @date 2025/5/28 14:05
     * @Param [baseFieldParseTable]
     **/
    public static TransformationSelectVisitor newInstanceNextLayer(BaseFieldParseTable baseFieldParseTable) {
        return new TransformationSelectVisitor(
                baseFieldParseTable.getLayer() + 1,
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap()
        );
    }

    /**
     * 场景1：
     * select
     * (select xx from tb) as xxx -- 这种语法
     * from tb
     * 场景2：
     * xxx in (select xxx from) -- 括号里面的这种语法
     *
     * @author liutangqi
     * @date 2025/5/21 15:09
     * @Param [parenthesedSelect]
     **/
    @Override
    public void visit(ParenthesedSelect parenthesedSelect) {
        //注意：这里层数是当前层，这个的解析结果需要和外层在同一层级
        Optional.ofNullable(parenthesedSelect.getSelect())
                .ifPresent(p -> p.accept(TransformationSelectVisitor.newInstanceCurLayer(this)));
    }

    @Override
    public void visit(PlainSelect plainSelect) {
        //1.select查询的每一项
        List<SelectItem<?>> selectItems = plainSelect.getSelectItems();
        TransformationSelectItemVisitor selectItemVisitor = TransformationSelectItemVisitor.newInstanceCurLayer(this);
        for (SelectItem<?> selectItem : selectItems) {
            selectItem.accept(selectItemVisitor);
        }

        //2.from嵌套的表
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem != null) {
            TransformationFromItemVisitor tfFromItemVisitor = TransformationFromItemVisitor.newInstanceCurLayer(this);
            fromItem.accept(tfFromItemVisitor);
        }


        //3.where条件
        Expression where = plainSelect.getWhere();
        if (where != null) {
            TransformationExpressionVisitor tfExpressionVisitor = TransformationExpressionVisitor.newInstanceCurLayer(this);
            //使用包装类进行转转，额外对整个Expression进行语法转换一次
            Expression tfExp = ExpressionWrapper.wrap(where).accept(tfExpressionVisitor);
            Optional.ofNullable(tfExp).ifPresent(p -> plainSelect.setWhere(p));
        }

        //4.join的表
        List<Join> joins = plainSelect.getJoins();
        if (CollectionUtils.isNotEmpty(joins)) {
            TransformationFromItemVisitor tfFromItemVisitor = TransformationFromItemVisitor.newInstanceCurLayer(this);
            for (Join join : joins) {
                //4.1 join的表
                FromItem joinRightItem = join.getRightItem();
                joinRightItem.accept(tfFromItemVisitor);
                //4.2 join的on条件
                List<Expression> tfExpressions = new ArrayList<>();
                for (Expression expression : join.getOnExpressions()) {
                    TransformationExpressionVisitor tfExpressionVisitor = TransformationExpressionVisitor.newInstanceCurLayer(this);
                    //使用包装类进行转转，额外对整个Expression进行语法转换一次
                    Expression tfExp = ExpressionWrapper.wrap(expression).accept(tfExpressionVisitor);
                    tfExpressions.add(Optional.ofNullable(tfExp).orElse(expression));
                }
                //处理后的结果赋值
                join.setOnExpressions(tfExpressions);
            }
        }

        //5. order by
        List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
        if (CollectionUtils.isNotEmpty(orderByElements)) {
            TransformationOrderByVisitor tfOrderByVisitor = TransformationOrderByVisitor.newInstanceCurLayer(this);
            for (OrderByElement orderByElement : orderByElements) {
                orderByElement.accept(tfOrderByVisitor);
            }
        }

        //6. group by
        GroupByElement groupBy = plainSelect.getGroupBy();
        if (groupBy != null) {
            TransformationGroupByVisitor tfGroupByVisitor = TransformationGroupByVisitor.newInstanceCurLayer(this);
            groupBy.accept(tfGroupByVisitor);
        }
    }

    /**
     * union
     *
     * @author liutangqi
     * @date 2025/5/21 15:14
     * @Param [setOpList]
     **/
    @Override
    public void visit(SetOperationList setOpList) {
        List<Select> selects = setOpList.getSelects();

        List<Select> resSelectBody = new ArrayList<>();
        for (int i = 0; i < selects.size(); i++) {
            Select select = selects.get(i);
            //解析每个union的语句自己拥有的字段信息
            FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
            select.accept(fieldParseTableSelectVisitor);

            //针对每个sql单独进行语法替换
            TransformationSelectVisitor tfSelectVisitor = TransformationSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor);
            select.accept(tfSelectVisitor);

            //维护加解密之后的语句
            resSelectBody.add(select);
        }
        setOpList.setSelects(resSelectBody);
    }

    @Override
    public void visit(WithItem withItem) {

    }

    /**
     * insert 语句 values后面的处理逻辑
     *
     * @author liutangqi
     * @date 2025/5/21 15:14
     * @Param [aThis]
     **/
    @Override
    public void visit(Values aThis) {
        ExpressionList<Expression> expressions = (ExpressionList<Expression>) aThis.getExpressions();
        for (int i = 0; i < expressions.size(); i++) {
            Expression exp = expressions.get(i);
            TransformationExpressionVisitor tfExpressionVisitor = TransformationExpressionVisitor.newInstanceCurLayer(this);
            //使用包装类进行转转，额外对整个Expression进行语法转换一次
            Expression tfExp = ExpressionWrapper.wrap(exp).accept(tfExpressionVisitor);
            expressions.set(i, Optional.ofNullable(tfExp).orElse(exp));
        }
    }

    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }

    @Override
    public void visit(TableStatement tableStatement) {

    }
}
