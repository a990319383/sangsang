package com.sangsang.visitor.pojoencrtptor;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.dto.PlaceholderFieldParseTable;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.visitor.EncryptorPojoVisitorUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
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
        //1.解析CTE语法内部的占位符
        EncryptorPojoVisitorUtil.cte(plainSelect.getWithItemsList(), this);

        //2.获取select的每一项，将其中 select (select a from xxx) from 这种语法的#{}占位符进行解析
        PlaceholderExpressionVisitor placeholderWhereExpressionVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(this);
        //2.1 上游字段和当前查询字段有对应关系的时候，查的字段的数量一定是相同的
        if (this.upstreamExpressionList.size() == plainSelect.getSelectItems().size()) {
            for (int i = 0; i < plainSelect.getSelectItems().size(); i++) {
                PlaceholderExpressionVisitor placeholderExpressionVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(this,
                        this.upstreamExpressionList.get(i));
                plainSelect.getSelectItems().get(i).getExpression().accept(placeholderExpressionVisitor);
            }
        }
        //2.2 上游字段和当前没对应关系时，使用同一个visitor即可
        else {
            plainSelect.getSelectItems()
                    .stream()
                    .forEach(f -> f.getExpression().accept(placeholderWhereExpressionVisitor));
        }


        //3.解析from 后面的 #{}占位符
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem != null) {
            PlaceholderSelectFromItemVisitor placeholderSelectFromItemVisitor = PlaceholderSelectFromItemVisitor.newInstanceCurLayer(this, this.upstreamExpressionList);
            fromItem.accept(placeholderSelectFromItemVisitor);
        }

        //4.将where 条件中的#{} 占位符进行解析
        Expression where = plainSelect.getWhere();
        if (where != null) {
            where.accept(placeholderWhereExpressionVisitor);
        }

        //5.解析join
        EncryptorPojoVisitorUtil.joins(plainSelect.getJoins(), this, this.getPlaceholderColumnTableMap());

        //6.解析having条件中的#{}占位符
        Expression having = plainSelect.getHaving();
        if (having != null) {
            having.accept(placeholderWhereExpressionVisitor);
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
        //1.解析挂在union语句上的CTE定义
        EncryptorPojoVisitorUtil.cte(setOperationList.getWithItemsList(), this);

        //2.依次解析union中的每条sql
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
        //1.CTE缺少内部查询的错误语法，不处理
        Select select = withItem.getSelect();
        if (select == null) {
            return;
        }

        //2.CTE内部是独立的查询作用域，单独解析其字段信息
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
        select.accept(fieldParseTableSelectVisitor);

        //3.使用CTE内部字段信息解析占位符，并和当前查询共用占位符结果集
        PlaceholderSelectVisitor placeholderSelectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor, this.getPlaceholderColumnTableMap());
        select.accept(placeholderSelectVisitor);

    }

    /**
     * insert into values 后面
     * SQL 形态              根节点                       子节点
     * ━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
     * VALUES (?)            ExpressionList               Parenthesis(?)
     * ────────────────────  ───────────────────────────  ────────────────────────────────
     * VALUES (?),(?)        ExpressionList               多个 Parenthesis(?)
     * ────────────────────  ───────────────────────────  ────────────────────────────────
     * VALUES (?,?)          ParenthesedExpressionList    多个实际字段值
     * ────────────────────  ───────────────────────────  ────────────────────────────────
     * VALUES (?,?),(?,?)    ExpressionList               多个 ParenthesedExpressionList
     *
     * @author liutangqi
     * @date 2026/11/2 17:57
     * @Param [aThis]
     **/
    @Override
    public void visit(Values aThis) {
        //1.外层是 ParenthesedExpressionList (对应形态3) 用例对应 i6
        if (aThis.getExpressions() instanceof ParenthesedExpressionList) {
            ParenthesedExpressionList<Expression> parenthesedExpressionList = (ParenthesedExpressionList) aThis.getExpressions();
            EncryptorPojoVisitorUtil.expressionList(this, this.upstreamExpressionList, parenthesedExpressionList);
        }

        //2.外层是 ExpressionList (对应形态1、2、4)
        if (aThis.getExpressions() instanceof ExpressionList) {
            ExpressionList expressionList = aThis.getExpressions();
            //2.1 形态1、对应测试用例 i16
            if (expressionList.size() == 1 && expressionList.getExpressions().get(0) instanceof Parenthesis) {
                Parenthesis parenthesis = (Parenthesis) expressionList.get(0);
                ParenthesedExpressionList<Expression> parenthesedExpressionList = new ParenthesedExpressionList<>();
                parenthesedExpressionList.add(parenthesis.getExpression());
                EncryptorPojoVisitorUtil.expressionList(this, this.upstreamExpressionList, parenthesedExpressionList);
                aThis.setExpressions(parenthesedExpressionList);
            }
            //2.2 形态2、对应测试用例 i17
            else if (expressionList.size() > 1 && expressionList.getExpressions().get(0) instanceof Parenthesis) {
                for (int i = 0; i < expressionList.size(); i++) {
                    Parenthesis parenthesis = (Parenthesis) expressionList.get(i);
                    ParenthesedExpressionList<Expression> parenthesedExpressionList = new ParenthesedExpressionList<>();
                    parenthesedExpressionList.add(parenthesis.getExpression());
                    EncryptorPojoVisitorUtil.expressionList(this, this.upstreamExpressionList, parenthesedExpressionList);
                    expressionList.set(i, parenthesedExpressionList);
                }
            }
            //2.3 形态4，对应测试用例 i1
            else if (expressionList.size() > 1 && expressionList.getExpressions().get(0) instanceof ParenthesedExpressionList) {
                for (Object exp : expressionList) {
                    ParenthesedExpressionList parenthesedExpressionList = (ParenthesedExpressionList) exp;
                    EncryptorPojoVisitorUtil.expressionList(this, this.upstreamExpressionList, parenthesedExpressionList);
                }
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
