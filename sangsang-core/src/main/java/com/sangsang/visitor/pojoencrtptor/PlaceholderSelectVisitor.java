package com.sangsang.visitor.pojoencrtptor;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.dto.PlaceholderFieldParseTable;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;

/**
 * 将select语句中的#{}占位符和数据库表字段对应起来
 *
 * @author liutangqi
 * @date 2024/7/12 10:34
 */
public class PlaceholderSelectVisitor extends PlaceholderFieldParseTable implements SelectVisitor {

    /**
     * 需要和上游关联时的上游的表达式
     * 例如： ? in (select xxx from table) 这种场景，这里存储的就是上游的表达式
     **/
    private List<? extends Expression> upstreamExpressionList;


    private PlaceholderSelectVisitor(int layer,
                                     Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap,
                                     Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap,
                                     Map<String, ColumnTableDto> placeholderColumnTableMap,
                                     List<? extends Expression> upstream) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap, placeholderColumnTableMap);
        this.upstreamExpressionList = Optional.ofNullable(upstream).orElse(new ArrayList<>());
    }

    /**
     * 返回当前层实例
     *
     * @author liutangqi
     * @date 2025/3/5 14:55
     * @Param [placeholderFieldParseTable]
     **/
    public static PlaceholderSelectVisitor newInstanceCurLayer(PlaceholderFieldParseTable placeholderFieldParseTable) {
        return PlaceholderSelectVisitor.newInstanceCurLayer(placeholderFieldParseTable, placeholderFieldParseTable.getPlaceholderColumnTableMap());
    }


    /**
     * 返回当前层实例
     *
     * @author liutangqi
     * @date 2025/3/5 14:55
     * @Param [placeholderFieldParseTable]
     **/
    public static PlaceholderSelectVisitor newInstanceCurLayer(PlaceholderFieldParseTable placeholderFieldParseTable,
                                                               List<? extends Expression> upstreamExpressionList) {
        return PlaceholderSelectVisitor.newInstanceCurLayer(placeholderFieldParseTable, placeholderFieldParseTable.getPlaceholderColumnTableMap(), upstreamExpressionList);
    }


    /**
     * 返回当前层实例
     *
     * @author liutangqi
     * @date 2025/3/5 14:55
     * @Param [placeholderFieldParseTable]
     **/
    public static PlaceholderSelectVisitor newInstanceCurLayer(BaseFieldParseTable fpt,
                                                               Map<String, ColumnTableDto> placeholderColumnTableMap,
                                                               List<? extends Expression> upstream) {
        return new PlaceholderSelectVisitor(
                fpt.getLayer(),
                fpt.getLayerSelectTableFieldMap(),
                fpt.getLayerFieldTableMap(),
                placeholderColumnTableMap,
                upstream
        );
    }

    /**
     * 返回当前层实例
     *
     * @author liutangqi
     * @date 2025/3/5 14:57
     * @Param [baseFieldParseTable, placeholderColumnTableMap]
     **/
    public static PlaceholderSelectVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable,
                                                               Map<String, ColumnTableDto> placeholderColumnTableMap) {
        return new PlaceholderSelectVisitor(baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap(),
                placeholderColumnTableMap,
                null);
    }

    /**
     * 返回下一层实例
     *
     * @author liutangqi
     * @date 2025/3/5 14:55
     * @Param [placeholderFieldParseTable]
     **/
    public static PlaceholderSelectVisitor newInstanceNextLayer(PlaceholderFieldParseTable placeholderFieldParseTable) {
        return new PlaceholderSelectVisitor((placeholderFieldParseTable.getLayer() + 1),
                placeholderFieldParseTable.getLayerSelectTableFieldMap(),
                placeholderFieldParseTable.getLayerFieldTableMap(),
                placeholderFieldParseTable.getPlaceholderColumnTableMap(),
                null);
    }

    /**
     * 返回下一层实例
     *
     * @author liutangqi
     * @date 2025/12/23 10:31
     * @Param [placeholderFieldParseTable, upstreamExpressionList]
     **/
    public static PlaceholderSelectVisitor newInstanceNextLayer(PlaceholderFieldParseTable placeholderFieldParseTable,
                                                                List<? extends Expression> upstreamExpressionList) {
        return new PlaceholderSelectVisitor((placeholderFieldParseTable.getLayer() + 1),
                placeholderFieldParseTable.getLayerSelectTableFieldMap(),
                placeholderFieldParseTable.getLayerFieldTableMap(),
                placeholderFieldParseTable.getPlaceholderColumnTableMap(),
                upstreamExpressionList);
    }

    /**
     * 场景1：
     * select
     * (select xx from tb)as xxx -- 这种语法
     * from tb
     * 场景2：
     * xxx in (select xxx from) -- 括号里面的这种语法
     *
     * @author liutangqi
     * @date 2025/3/17 14:10
     * @Param [parenthesedSelect]
     **/
    @Override
    public void visit(ParenthesedSelect subSelect) {
        //处理子查询内容（注意：这里层数是当前层，这个的解析结果需要和外层在同一层级）
        Optional.ofNullable(subSelect.getSelect())
                .ifPresent(p -> p.accept(PlaceholderSelectVisitor.newInstanceCurLayer(this, this.upstreamExpressionList)));
    }

    @Override
    public void visit(PlainSelect plainSelect) {
        //1.获取select的每一项，将其中 select (select a from xxx) from 这种语法的#{}占位符进行解析
        PlaceholderExpressionVisitor placeholderWhereExpressionVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(this);
        //1.1 上游字段和当前查询字段有对应关系的时候，查的字段的数量一定是相同的
        if (this.upstreamExpressionList.size() == plainSelect.getSelectItems().size()) {
            for (int i = 0; i < plainSelect.getSelectItems().size(); i++) {
                PlaceholderExpressionVisitor placeholderExpressionVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(this,
                        this.upstreamExpressionList.get(i));
                plainSelect.getSelectItems().get(i).getExpression().accept(placeholderExpressionVisitor);
            }
        }
        //1.2 上游字段和当前没对应关系时，使用同一个visitor即可
        else {
            plainSelect.getSelectItems()
                    .stream()
                    .forEach(f -> f.getExpression().accept(placeholderWhereExpressionVisitor));
        }


        //2.解析from 后面的 #{}占位符
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem != null) {
            PlaceholderSelectFromItemVisitor placeholderSelectFromItemVisitor = PlaceholderSelectFromItemVisitor.newInstanceCurLayer(this, this.upstreamExpressionList);
            fromItem.accept(placeholderSelectFromItemVisitor);
        }

        //3.将where 条件中的#{} 占位符进行解析
        Expression where = plainSelect.getWhere();
        if (where != null) {
            where.accept(placeholderWhereExpressionVisitor);
        }

        //4.解析join
        List<Join> joins = plainSelect.getJoins();
        if (CollectionUtils.isNotEmpty(joins)) {
            PlaceholderSelectFromItemVisitor phFromItemVisitor = PlaceholderSelectFromItemVisitor.newInstanceCurLayer(this);
            for (Join join : joins) {
                //4.1解析join的表
                FromItem joinRightItem = join.getRightItem();
                joinRightItem.accept(phFromItemVisitor);
                //4.2解析on
                for (Expression expression : join.getOnExpressions()) {
                    expression.accept(PlaceholderExpressionVisitor.newInstanceCurLayer(this));
                }
            }
        }
    }

    /**
     * union  ，union all 语法，将每个sql分开解析，获取其中的#{}占位符
     *
     * @author liutangqi
     * @date 2024/7/17 17:26
     * @Param [setOperationList]
     **/
    @Override
    public void visit(SetOperationList setOperationList) {
        List<Select> selects = setOperationList.getSelects();
        for (Select select : selects) {
            //单独解析这条sql
            FieldParseParseTableSelectVisitor fieldParseParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
            select.accept(fieldParseParseTableSelectVisitor);

            //用解析后的结果，去解析#{}占位符  (字段所属信息从上面解析结果中取，存放占位符的解析结果的Map用当前的 )
            PlaceholderSelectVisitor placeholderSelectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(fieldParseParseTableSelectVisitor, this.getPlaceholderColumnTableMap());
            select.accept(placeholderSelectVisitor);

        }
    }

    @Override
    public void visit(WithItem withItem) {

    }

    @Override
    public void visit(Values aThis) {
        ExpressionList<Expression> expressions = (ExpressionList<Expression>) aThis.getExpressions();
        for (Expression expressionList : expressions) {
            //1. insert 语句后面的值 (xxx,xxx),(xxx,xxx)这种list
            if (expressionList instanceof ExpressionList) {
                ExpressionList eList = (ExpressionList) expressionList;
                for (int i = 0; i < eList.size(); i++) {
                    Expression curExp = (Expression) eList.get(i);
                    Expression upstreamExpression = this.upstreamExpressionList.get(i);
                    JsqlparserUtil.parseWhereColumTable(this,
                            upstreamExpression,
                            curExp,
                            this.getPlaceholderColumnTableMap());

                }
            }
        }

        //2. insert 语句后面的值 (xxx,xxx) 这种单个的值
        if (!(expressions.get(0) instanceof ExpressionList)) {
            for (int i = 0; i < expressions.size(); i++) {
                Expression curExp = (Expression) expressions.get(i);
                Expression upstreamExpression = this.upstreamExpressionList.get(i);
                JsqlparserUtil.parseWhereColumTable(this,
                        upstreamExpression,
                        curExp,
                        this.getPlaceholderColumnTableMap());
            }
        }
    }

    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }

    @Override
    public void visit(TableStatement tableStatement) {

    }

}
