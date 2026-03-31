package com.sangsang.visitor.fielddefault;

import com.sangsang.domain.annos.fielddefault.FieldDefault;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.util.ExpressionsUtil;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;

/**
 * @author liutangqi
 * @date 2025/7/20 10:32
 */
public class FieldDefaultSelectVisitor extends BaseFieldParseTable implements SelectVisitor {


    /**
     * 上游insert的字段头上依次的@FieldDefault信息，并且是满足执行条件的
     */
    private List<FieldDefault> upstreamFieldDefaultColumns;

    /**
     * 获取当前层解析实例
     *
     * @author liutangqi
     * @date 2025/7/21 10:35
     * @Param [baseFieldParseTable, upstreamFieldDefaultColumns]
     **/
    public static FieldDefaultSelectVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable,
                                                                List<FieldDefault> upstreamFieldDefaultColumns) {
        return new FieldDefaultSelectVisitor(upstreamFieldDefaultColumns,
                baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap());
    }

    private FieldDefaultSelectVisitor(List<FieldDefault> upstreamFieldDefaultColumns,
                                      int layer,
                                      Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap,
                                      Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
        this.upstreamFieldDefaultColumns = upstreamFieldDefaultColumns;
    }


    /**
     * 场景1：
     * select
     * (select xx from tb)as xxx -- 这种语法
     * from tb
     * 场景2：
     * xxx in (select xxx from) -- 括号里面的这种语法
     * 场景3： （我们这里处理的就是这个场景，将需要维护默认值的字段进行补齐）
     * insert into (select xxx from)
     *
     * @author liutangqi
     * @date 2025/7/20 10:33
     * @Param [parenthesedSelect]
     **/
    @Override
    public void visit(ParenthesedSelect parenthesedSelect) {
        Optional.ofNullable(parenthesedSelect.getSelect())
                .ifPresent(p -> p.accept(this));
    }

    /**
     * 普通的select查询
     * 如果需要维护的字段不存在的话，则需要在select的查询字段中新增策略拥有的默认值字段
     *
     * @author liutangqi
     * @date 2025/7/20 10:41
     * @Param [plainSelect]
     **/
    @Override
    public void visit(PlainSelect plainSelect) {
        //1.存在select * 不做处理
        //具体不处理的原因见：com.sangsang.visitor.fielddefault.FieldDefaultStatementVisitor.visit(net.sf.jsqlparser.statement.insert.Insert)
        //或搜索日志:【sangsang】insert 存在*。此情况无法设置默认值!!!请规范语法!!!
        List<SelectItem<?>> selectItems = plainSelect.getSelectItems();
        SelectItem<?> allColumnItem = plainSelect.getSelectItems().stream()
                .filter(f -> (f.getExpression() instanceof AllColumns) || (f.getExpression() instanceof AllTableColumns))
                .findAny()
                .orElse(null);
        if (allColumnItem != null) {
            return;
        }

        //2.依次处理每个字段
        List<SelectItem<?>> itemRes = new ArrayList<>();
        for (int i = 0; i < upstreamFieldDefaultColumns.size(); i++) {
            //2.1先默认不修改原表达式
            if (i < selectItems.size()) {
                itemRes.add(selectItems.get(i));
            }
            //2.2 需要设置默认值的字段原sql就存在，并且开启了强制覆盖 则将默认值给原sql用if的方式给替换了
            if (i < selectItems.size() && upstreamFieldDefaultColumns.get(i) != null && upstreamFieldDefaultColumns.get(i).mandatoryOverride()) {
                Expression fieldDefaultExp = ExpressionsUtil.buildFieldDefaultExp(upstreamFieldDefaultColumns.get(i).value());
                Function ifFunction = ExpressionsUtil.buildAffirmativeIf(selectItems.get(i).getExpression(), fieldDefaultExp);
                //这里的场景应该不需要处理别名
                itemRes.set(i, new SelectItem(ifFunction));
            }
            //2.3 需要设置默认值的字段原sql不存在，则新增
            if (i >= selectItems.size() && upstreamFieldDefaultColumns.get(i) != null) {
                Expression fieldDefaultExp = ExpressionsUtil.buildFieldDefaultExp(upstreamFieldDefaultColumns.get(i).value());
                //这里的场景应该不需要处理别名
                itemRes.add(i, new SelectItem(fieldDefaultExp));
            }
        }

        //3.处理结果赋值
        plainSelect.setSelectItems(itemRes);

    }

    /**
     * union
     *
     * @author liutangqi
     * @date 2025/7/20 10:45
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

            //依次维护默认值
            FieldDefaultSelectVisitor fdSelectVisitor = FieldDefaultSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor, this.upstreamFieldDefaultColumns);
            select.accept(fdSelectVisitor);

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
     * @date 2025/7/20 10:47
     * @Param [aThis]
     **/
    @Override
    public void visit(Values aThis) {
        ExpressionList<Expression> expressions = (ExpressionList<Expression>) aThis.getExpressions();
        for (Expression expressionList : expressions) {
            //1. insert 语句后面的值 (xxx,xxx),(xxx,xxx)这种list
            if (expressionList instanceof ExpressionList) {
                ExpressionList eList = (ExpressionList) expressionList;
                JsqlparserUtil.completeFiledDefaultValues(this.upstreamFieldDefaultColumns, eList);
            }
        }

        //2. insert 语句后面的值 (xxx,xxx) 这种单个的值
        if (!(expressions.get(0) instanceof ExpressionList)) {
            ExpressionList eList = (ExpressionList) expressions;
            JsqlparserUtil.completeFiledDefaultValues(this.upstreamFieldDefaultColumns, eList);
        }
    }

    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }

    @Override
    public void visit(TableStatement tableStatement) {

    }


}
