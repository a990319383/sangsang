package com.sangsang.visitor.transformation.oracle2mysql;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.dto.O2MTfExpressionDto;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import lombok.Getter;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.arithmetic.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.conditional.XorExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.Select;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 注意：由于这里入参有alias ，为了避免不同字段之间错乱，这个visitor不能复用
 *
 * @author liutangqi
 * @date 2026/1/5 16:29
 */
public class O2MTfExpressionVisitor extends BaseFieldParseTable implements ExpressionVisitor {

    /**
     * 当前表达式的别名
     */
    private Alias alias;

    /**
     * 处理后的最终结果
     * 需要直接删除时这个字段返回一个build的默认值
     */
    @Getter
    private O2MTfExpressionDto process = O2MTfExpressionDto.DEFAULT;

    /**
     * 获得当前层实例
     *
     * @author liutangqi
     * @date 2026/1/5 16:30
     * @Param [baseFieldParseTable]
     **/
    public static O2MTfExpressionVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return newInstanceCurLayer(null, baseFieldParseTable);
    }

    /**
     * 获得当前层实例
     *
     * @author liutangqi
     * @date 2026/1/5 16:30
     * @Param [baseFieldParseTable]
     **/
    public static O2MTfExpressionVisitor newInstanceCurLayer(Alias alias, BaseFieldParseTable baseFieldParseTable) {
        return new O2MTfExpressionVisitor(alias, baseFieldParseTable.getLayer(), baseFieldParseTable.getLayerSelectTableFieldMap(), baseFieldParseTable.getLayerFieldTableMap());
    }

    private O2MTfExpressionVisitor(Alias alias, int layer, Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
        this.alias = alias;
    }

    @Override
    public void visit(BitwiseRightShift aThis) {

    }

    @Override
    public void visit(BitwiseLeftShift aThis) {

    }

    @Override
    public void visit(NullValue nullValue) {

    }

    @Override
    public void visit(Function function) {
        //todo-ltq 窗口函数
    }

    @Override
    public void visit(SignedExpression signedExpression) {

    }

    @Override
    public void visit(JdbcParameter jdbcParameter) {

    }

    @Override
    public void visit(JdbcNamedParameter jdbcNamedParameter) {

    }

    @Override
    public void visit(DoubleValue doubleValue) {

    }

    @Override
    public void visit(LongValue longValue) {

    }

    @Override
    public void visit(HexValue hexValue) {

    }

    @Override
    public void visit(DateValue dateValue) {

    }

    @Override
    public void visit(TimeValue timeValue) {

    }

    @Override
    public void visit(TimestampValue timestampValue) {

    }

    @Override
    public void visit(Parenthesis parenthesis) {

    }

    @Override
    public void visit(StringValue stringValue) {

    }

    @Override
    public void visit(Addition addition) {

    }

    @Override
    public void visit(Division division) {

    }

    @Override
    public void visit(IntegerDivision division) {

    }

    @Override
    public void visit(Multiplication multiplication) {

    }

    @Override
    public void visit(Subtraction subtraction) {

    }

    @Override
    public void visit(AndExpression andExpression) {
        //处理左边表达式
        Expression leftExpression = andExpression.getLeftExpression();
        O2MTfExpressionVisitor leftO2MTfExpressionVisitor = O2MTfExpressionVisitor.newInstanceCurLayer(this);
        leftExpression.accept(leftO2MTfExpressionVisitor);


        //处理右边表达式
        Expression rightExpression = andExpression.getRightExpression();
        O2MTfExpressionVisitor rightO2MTfExpressionVisitor = O2MTfExpressionVisitor.newInstanceCurLayer(this);
        rightExpression.accept(rightO2MTfExpressionVisitor);

        //将左右表达式的处理结果进行合并，左右两边的表达式都不保留的情况，将两个表达式的范围进行合并
        if (!leftO2MTfExpressionVisitor.getProcess().isRetainExpression() && !rightO2MTfExpressionVisitor.getProcess().isRetainExpression()) {
            this.process = O2MTfExpressionDto.builder()
                    .retainExpression(false)
                    .ge(CollectionUtils.getMax(leftO2MTfExpressionVisitor.getProcess().getGe(), rightO2MTfExpressionVisitor.getProcess().getGe()))
                    .le(CollectionUtils.getMin(leftO2MTfExpressionVisitor.getProcess().getLe(), rightO2MTfExpressionVisitor.getProcess().getLe()))
                    .build();
        }


    }

    @Override
    public void visit(OrExpression orExpression) {

    }

    @Override
    public void visit(XorExpression orExpression) {

    }

    /**
     * 处理between行号
     *
     * @author liutangqi
     * @date 2026/1/5 16:59
     * @Param [between]
     **/
    @Override
    public void visit(Between between) {
        Expression leftExpression = between.getLeftExpression();
        Expression startExp = between.getBetweenExpressionStart();
        Expression endExp = between.getBetweenExpressionEnd();
        //1.只有leftExpression是行号字段时，才处理
        if (!(leftExpression instanceof Column)) {
            return;
        }

        //2.左表达式和当前层查询字段做匹配
        ColumnTableDto columnTableDto = JsqlparserUtil.parseColumn((Column) leftExpression, this);

        //3.如果当前字段不是行号字段，则不处理
        if (!columnTableDto.isRowNumber()) {
            return;
        }

        //4.当前是行号字段，说明这个是用来分页的，那么这个表达式就不需要保留了，并记录当前行号范围
        Long ge = JsqlparserUtil.tfParseRowNumber(startExp);
        Long le = JsqlparserUtil.tfParseRowNumber(endExp);

        this.process = O2MTfExpressionDto.builder().retainExpression(false).ge(ge).le(le).build();
    }

    @Override
    public void visit(OverlapsCondition overlapsCondition) {

    }

    @Override
    public void visit(EqualsTo equalsTo) {

    }

    /**
     * >  当一边是行号，一边是字段时，表示这个表达式是用来分页的
     * 注意：这里表达式肯定是在where后面的，这里去解析Column信息时，是从layerFieldTableMap中获取的，如果这里是凭空直接调用的rownumber关键字的话，解析结果集中是没有的，还需要再手动判断一次
     *
     * @author liutangqi
     * @date 2026/1/9 15:29
     * @Param [greaterThan]
     **/
    @Override
    public void visit(GreaterThan greaterThan) {
        Expression leftExpression = greaterThan.getLeftExpression();
        Expression rightExpression = greaterThan.getRightExpression();
        //1.左边是行号字段时    rownumber > num
        if (leftExpression instanceof Column
                && (JsqlparserUtil.rowNumber((Column) leftExpression) ||
                JsqlparserUtil.parseColumn((Column) leftExpression, this).isRowNumber())) {
            Long num = JsqlparserUtil.tfParseRowNumber(rightExpression);
            this.process = O2MTfExpressionDto.builder().retainExpression(false).ge(num + 1).build();
        }
        //2.右边是行号字段时  num > rownumber
        if (rightExpression instanceof Column
                && (JsqlparserUtil.rowNumber((Column) rightExpression) ||
                JsqlparserUtil.parseColumn((Column) rightExpression, this).isRowNumber())) {
            Long num = JsqlparserUtil.tfParseRowNumber(leftExpression);
            this.process = O2MTfExpressionDto.builder().retainExpression(false).le(num - 1).build();
        }
    }

    /**
     * >=
     * 注意：这里表达式肯定是在where后面的，这里去解析Column信息时，是从layerFieldTableMap中获取的，如果这里是凭空直接调用的rownumber关键字的话，解析结果集中是没有的，还需要再手动判断一次
     *
     * @author liutangqi
     * @date 2026/1/9 15:54
     * @Param [minorThanEquals]
     **/
    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {
        Expression leftExpression = greaterThanEquals.getLeftExpression();
        Expression rightExpression = greaterThanEquals.getRightExpression();
        //1.左边是行号字段时    rownumber >= num
        if (leftExpression instanceof Column
                && (JsqlparserUtil.rowNumber((Column) leftExpression) ||
                JsqlparserUtil.parseColumn((Column) leftExpression, this).isRowNumber())) {
            Long num = JsqlparserUtil.tfParseRowNumber(rightExpression);
            this.process = O2MTfExpressionDto.builder().retainExpression(false).ge(num).build();
        }
        //2.右边是行号字段时  num >= rownumber
        if (rightExpression instanceof Column
                && (JsqlparserUtil.rowNumber((Column) rightExpression) ||
                JsqlparserUtil.parseColumn((Column) rightExpression, this).isRowNumber())) {
            Long num = JsqlparserUtil.tfParseRowNumber(leftExpression);
            this.process = O2MTfExpressionDto.builder().retainExpression(false).le(num).build();
        }
    }

    @Override
    public void visit(InExpression inExpression) {

    }

    @Override
    public void visit(FullTextSearch fullTextSearch) {

    }

    @Override
    public void visit(IsNullExpression isNullExpression) {

    }

    @Override
    public void visit(IsBooleanExpression isBooleanExpression) {

    }

    @Override
    public void visit(LikeExpression likeExpression) {

    }

    /**
     * <
     * 注意：这里表达式肯定是在where后面的，这里去解析Column信息时，是从layerFieldTableMap中获取的，如果这里是凭空直接调用的rownumber关键字的话，解析结果集中是没有的，还需要再手动判断一次
     *
     * @author liutangqi
     * @date 2026/1/9 15:54
     * @Param [minorThanEquals]
     **/
    @Override
    public void visit(MinorThan minorThan) {
        Expression leftExpression = minorThan.getLeftExpression();
        Expression rightExpression = minorThan.getRightExpression();
        //1.左边是行号字段时    rownumber < num
        if (leftExpression instanceof Column
                && (JsqlparserUtil.rowNumber((Column) leftExpression) ||
                JsqlparserUtil.parseColumn((Column) leftExpression, this).isRowNumber())) {
            Long num = JsqlparserUtil.tfParseRowNumber(rightExpression);
            this.process = O2MTfExpressionDto.builder().retainExpression(false).le(num - 1).build();
        }
        //2.右边是行号字段时  num < rownumber
        if (rightExpression instanceof Column
                && (JsqlparserUtil.rowNumber((Column) rightExpression) ||
                JsqlparserUtil.parseColumn((Column) rightExpression, this).isRowNumber())) {
            Long num = JsqlparserUtil.tfParseRowNumber(leftExpression);
            this.process = O2MTfExpressionDto.builder().retainExpression(false).ge(num + 1).build();
        }
    }

    /**
     * <=
     * 注意：这里表达式肯定是在where后面的，这里去解析Column信息时，是从layerFieldTableMap中获取的，如果这里是凭空直接调用的rownumber关键字的话，解析结果集中是没有的，还需要再手动判断一次
     *
     * @author liutangqi
     * @date 2026/1/9 15:54
     * @Param [minorThanEquals]
     **/
    @Override
    public void visit(MinorThanEquals minorThanEquals) {
        Expression leftExpression = minorThanEquals.getLeftExpression();
        Expression rightExpression = minorThanEquals.getRightExpression();
        //1.左边是行号字段时    rownumber <= num
        if (leftExpression instanceof Column
                && (JsqlparserUtil.rowNumber((Column) leftExpression) ||
                JsqlparserUtil.parseColumn((Column) leftExpression, this).isRowNumber())) {
            Long num = JsqlparserUtil.tfParseRowNumber(rightExpression);
            this.process = O2MTfExpressionDto.builder().retainExpression(false).le(num).build();
        }
        //2.右边是行号字段时  num <= rownumber
        if (rightExpression instanceof Column
                && (JsqlparserUtil.rowNumber((Column) rightExpression) ||
                JsqlparserUtil.parseColumn((Column) rightExpression, this).isRowNumber())) {
            Long num = JsqlparserUtil.tfParseRowNumber(leftExpression);
            this.process = O2MTfExpressionDto.builder().retainExpression(false).ge(num).build();
        }
    }

    @Override
    public void visit(NotEqualsTo notEqualsTo) {

    }

    @Override
    public void visit(DoubleAnd doubleAnd) {

    }

    @Override
    public void visit(Contains contains) {

    }

    @Override
    public void visit(ContainedBy containedBy) {

    }

    @Override
    public void visit(ParenthesedSelect selectBody) {

    }

    /**
     * 这里处理的是对应Oracle的 ROWNUM 关键字这种语法
     *
     * @author liutangqi
     * @date 2026/1/5 16:53
     * @Param [tableColumn]
     **/
    @Override
    public void visit(Column tableColumn) {
        // rownumber肯定是存在对应层级的查询字段中的，所以这里通过layerSelectTableFieldMap进行查找
        ColumnTableDto layerSelectColumnTableDto = JsqlparserUtil.parseColumn(tableColumn, this.alias, this);
        //当前字段属于行号字段，则将当前表达式不需要在结果集中保留
        if (layerSelectColumnTableDto.isRowNumber()) {
            this.process = O2MTfExpressionDto.NOT_RETAIN;
        }
    }

    @Override
    public void visit(CaseExpression caseExpression) {

    }

    @Override
    public void visit(WhenClause whenClause) {

    }

    @Override
    public void visit(ExistsExpression existsExpression) {

    }

    @Override
    public void visit(MemberOfExpression memberOfExpression) {

    }

    @Override
    public void visit(AnyComparisonExpression anyComparisonExpression) {

    }

    @Override
    public void visit(Concat concat) {

    }

    @Override
    public void visit(Matches matches) {

    }

    @Override
    public void visit(BitwiseAnd bitwiseAnd) {

    }

    @Override
    public void visit(BitwiseOr bitwiseOr) {

    }

    @Override
    public void visit(BitwiseXor bitwiseXor) {

    }

    @Override
    public void visit(CastExpression cast) {

    }

    @Override
    public void visit(Modulo modulo) {

    }

    @Override
    public void visit(AnalyticExpression aexpr) {

    }

    @Override
    public void visit(ExtractExpression eexpr) {

    }

    @Override
    public void visit(IntervalExpression iexpr) {

    }

    @Override
    public void visit(OracleHierarchicalExpression oexpr) {

    }

    @Override
    public void visit(RegExpMatchOperator rexpr) {

    }

    @Override
    public void visit(JsonExpression jsonExpr) {

    }

    @Override
    public void visit(JsonOperator jsonExpr) {

    }

    @Override
    public void visit(UserVariable var) {

    }

    @Override
    public void visit(NumericBind bind) {

    }

    @Override
    public void visit(KeepExpression aexpr) {

    }

    @Override
    public void visit(MySQLGroupConcat groupConcat) {

    }

    @Override
    public void visit(ExpressionList<?> expressionList) {

    }

    @Override
    public void visit(RowConstructor<?> rowConstructor) {

    }

    @Override
    public void visit(RowGetExpression rowGetExpression) {

    }

    @Override
    public void visit(OracleHint hint) {

    }

    @Override
    public void visit(TimeKeyExpression timeKeyExpression) {

    }

    @Override
    public void visit(DateTimeLiteralExpression literal) {

    }

    @Override
    public void visit(NotExpression aThis) {

    }

    @Override
    public void visit(NextValExpression aThis) {

    }

    @Override
    public void visit(CollateExpression aThis) {

    }

    @Override
    public void visit(SimilarToExpression aThis) {

    }

    @Override
    public void visit(ArrayExpression aThis) {

    }

    @Override
    public void visit(ArrayConstructor aThis) {

    }

    @Override
    public void visit(VariableAssignment aThis) {

    }

    @Override
    public void visit(XMLSerializeExpr aThis) {

    }

    @Override
    public void visit(TimezoneExpression aThis) {

    }

    @Override
    public void visit(JsonAggregateFunction aThis) {

    }

    @Override
    public void visit(JsonFunction aThis) {

    }

    @Override
    public void visit(ConnectByRootOperator aThis) {

    }

    @Override
    public void visit(OracleNamedFunctionParameter aThis) {

    }

    @Override
    public void visit(AllColumns allColumns) {

    }

    @Override
    public void visit(AllTableColumns allTableColumns) {

    }

    @Override
    public void visit(AllValue allValue) {

    }

    @Override
    public void visit(IsDistinctExpression isDistinctExpression) {

    }

    @Override
    public void visit(GeometryDistance geometryDistance) {

    }

    @Override
    public void visit(Select selectBody) {

    }

    @Override
    public void visit(TranscodingFunction transcodingFunction) {

    }

    @Override
    public void visit(TrimFunction trimFunction) {

    }

    @Override
    public void visit(RangeExpression rangeExpression) {

    }

    @Override
    public void visit(TSQLLeftJoin tsqlLeftJoin) {

    }

    @Override
    public void visit(TSQLRightJoin tsqlRightJoin) {

    }
}
