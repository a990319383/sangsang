package com.sangsang.visitor.transformation.oracle2mysql;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.dto.PageWhereResult;
import com.sangsang.util.JsqlparserUtil;
import lombok.Getter;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.arithmetic.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.conditional.XorExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;

import java.util.List;
import java.util.Map;

/**
 * Oracle 分页 where 解析 visitor。
 *
 * <p>该 visitor 的职责只有一个：从 where 表达式树中提炼分页边界。</p>
 *
 * <p>解析结果统一落到 {@link PageWhereResult} 中：</p>
 * <p>1. hasPage 表示是否识别到了分页条件。</p>
 * <p>2. retainWhere 表示仍需保留的业务条件。</p>
 * <p>3. ge / le 表示分页上下界。</p>
 * <p>4. emptyResult 表示当前子树已经确定为空结果。</p>
 *
 * <p>当前 visitor 只识别 Oracle 分页常见写法：</p>
 * <p>1. rownum / row_id 的 {@code > >= < <= between}</p>
 * <p>2. 上述条件通过 and 组合</p>
 *
 * <p>其它表达式一律按普通业务条件处理并保留原样。</p>
 *
 * @author liutangqi
 * @date 2026/3/24 10:40
 */
public class OraclePageWhereExpressionVisitor extends BaseFieldParseTable implements ExpressionVisitor {

    /**
     * 当前表达式树处理后的结果。
     */
    @Getter
    private PageWhereResult process;

    public static OraclePageWhereExpressionVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new OraclePageWhereExpressionVisitor(
                baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap()
        );
    }

    private OraclePageWhereExpressionVisitor(int layer,
                                             Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap,
                                             Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
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

    /**
     * 递归处理 and。
     *
     * <p>分页条件通过 and 组合时，需要把左右子树的结果合并：</p>
     * <p>1. ge 取较大值。</p>
     * <p>2. le 取较小值。</p>
     * <p>3. retainWhere 把左右保留条件重新拼成 and。</p>
     */
    @Override
    public void visit(AndExpression andExpression) {
        PageWhereResult leftResult = visitChild(andExpression.getLeftExpression());
        PageWhereResult rightResult = visitChild(andExpression.getRightExpression());
        if (!leftResult.isHasPage() && !rightResult.isHasPage()) {
            this.process = PageWhereResult.noPage(andExpression);
            return;
        }

        Long ge = max(leftResult.getGe(), rightResult.getGe());
        Long le = min(leftResult.getLe(), rightResult.getLe());
        boolean emptyResult = leftResult.isEmptyResult()
                || rightResult.isEmptyResult()
                || invalidRange(ge, le);
        Expression retainWhere = buildAndExpression(leftResult.getRetainWhere(), rightResult.getRetainWhere());
        this.process = PageWhereResult.page(retainWhere, ge, le, emptyResult);
    }

    @Override
    public void visit(OrExpression orExpression) {

    }

    @Override
    public void visit(XorExpression orExpression) {

    }

    /**
     * 处理 between 分页条件。
     */
    @Override
    public void visit(Between between) {
        RowNumberColumnType columnType = resolveRowNumberColumnType(between.getLeftExpression());
        if (columnType == RowNumberColumnType.NONE) {
            return;
        }

        Long ge = JsqlparserUtil.tfParseRowNumber(between.getBetweenExpressionStart());
        Long le = JsqlparserUtil.tfParseRowNumber(between.getBetweenExpressionEnd());
        if (ge == null || le == null) {
            return;
        }
        this.process = buildPageResult(columnType, ge, le);
    }

    @Override
    public void visit(OverlapsCondition overlapsCondition) {

    }

    @Override
    public void visit(EqualsTo equalsTo) {

    }

    /**
     * 处理大于分页条件。
     */
    @Override
    public void visit(GreaterThan greaterThan) {
        PageWhereResult leftResult = buildLeftLowerBoundResult(
                greaterThan.getLeftExpression(),
                greaterThan.getRightExpression(),
                1
        );
        if (leftResult != null) {
            this.process = leftResult;
            return;
        }

        this.process = buildRightUpperBoundResult(
                greaterThan.getRightExpression(),
                greaterThan.getLeftExpression(),
                -1
        );
    }

    /**
     * 处理大于等于分页条件。
     */
    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {
        PageWhereResult leftResult = buildLeftLowerBoundResult(
                greaterThanEquals.getLeftExpression(),
                greaterThanEquals.getRightExpression(),
                0
        );
        if (leftResult != null) {
            this.process = leftResult;
            return;
        }

        this.process = buildRightUpperBoundResult(
                greaterThanEquals.getRightExpression(),
                greaterThanEquals.getLeftExpression(),
                0
        );
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
     * 处理小于分页条件。
     */
    @Override
    public void visit(MinorThan minorThan) {
        PageWhereResult leftResult = buildLeftUpperBoundResult(
                minorThan.getLeftExpression(),
                minorThan.getRightExpression(),
                -1
        );
        if (leftResult != null) {
            this.process = leftResult;
            return;
        }

        this.process = buildRightLowerBoundResult(
                minorThan.getRightExpression(),
                minorThan.getLeftExpression(),
                1
        );
    }

    /**
     * 处理小于等于分页条件。
     */
    @Override
    public void visit(MinorThanEquals minorThanEquals) {
        PageWhereResult leftResult = buildLeftUpperBoundResult(
                minorThanEquals.getLeftExpression(),
                minorThanEquals.getRightExpression(),
                0
        );
        if (leftResult != null) {
            this.process = leftResult;
            return;
        }

        this.process = buildRightLowerBoundResult(
                minorThanEquals.getRightExpression(),
                minorThanEquals.getLeftExpression(),
                0
        );
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

    @Override
    public void visit(Column tableColumn) {

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

    /**
     * 子表达式统一递归入口。
     *
     * <p>如果子 visitor 没有识别出分页条件，则默认把该子表达式当成普通业务条件保留。</p>
     */
    private PageWhereResult visitChild(Expression expression) {
        OraclePageWhereExpressionVisitor childVisitor = OraclePageWhereExpressionVisitor.newInstanceCurLayer(this);
        expression.accept(childVisitor);
        return childVisitor.getProcess() == null ? PageWhereResult.noPage(expression) : childVisitor.getProcess();
    }

    /**
     * 当行号字段在左边时，构造分页下界。
     */
    private PageWhereResult buildLeftLowerBoundResult(Expression leftExpression, Expression rightExpression, long delta) {
        RowNumberColumnType columnType = resolveRowNumberColumnType(leftExpression);
        if (columnType == RowNumberColumnType.NONE) {
            return null;
        }

        Long value = JsqlparserUtil.tfParseRowNumber(rightExpression);
        if (value == null) {
            return null;
        }
        return buildPageResult(columnType, value + delta, null);
    }

    /**
     * 当行号字段在左边时，构造分页上界。
     */
    private PageWhereResult buildLeftUpperBoundResult(Expression leftExpression, Expression rightExpression, long delta) {
        RowNumberColumnType columnType = resolveRowNumberColumnType(leftExpression);
        if (columnType == RowNumberColumnType.NONE) {
            return null;
        }

        Long value = JsqlparserUtil.tfParseRowNumber(rightExpression);
        if (value == null) {
            return null;
        }
        return buildPageResult(columnType, null, value + delta);
    }

    /**
     * 当行号字段在右边时，构造分页下界。
     */
    private PageWhereResult buildRightLowerBoundResult(Expression rightExpression, Expression leftExpression, long delta) {
        RowNumberColumnType columnType = resolveRowNumberColumnType(rightExpression);
        if (columnType == RowNumberColumnType.NONE) {
            return null;
        }

        Long value = JsqlparserUtil.tfParseRowNumber(leftExpression);
        if (value == null) {
            return null;
        }
        return buildPageResult(columnType, value + delta, null);
    }

    /**
     * 当行号字段在右边时，构造分页上界。
     */
    private PageWhereResult buildRightUpperBoundResult(Expression rightExpression, Expression leftExpression, long delta) {
        RowNumberColumnType columnType = resolveRowNumberColumnType(rightExpression);
        if (columnType == RowNumberColumnType.NONE) {
            return null;
        }

        Long value = JsqlparserUtil.tfParseRowNumber(leftExpression);
        if (value == null) {
            return null;
        }
        return buildPageResult(columnType, null, value + delta);
    }

    /**
     * 判断当前表达式对应的行号字段类型。
     *
     * <p>必须区分原始 ROWNUM 和 row_id 这类别名字段：</p>
     * <p>1. 原始 ROWNUM 带有 Oracle 自己的求值语义。</p>
     * <p>2. row_id 这种别名字段是外层查询暴露出来的行号结果。</p>
     */
    private RowNumberColumnType resolveRowNumberColumnType(Expression expression) {
        if (!(expression instanceof Column)) {
            return RowNumberColumnType.NONE;
        }

        Column column = (Column) expression;
        if (JsqlparserUtil.rowNumber(column)) {
            return RowNumberColumnType.RAW_ROWNUM;
        }

        ColumnTableDto columnTableDto = JsqlparserUtil.parseColumn(column, this);
        return columnTableDto.isRowNumber() ? RowNumberColumnType.ROW_NUMBER_ALIAS : RowNumberColumnType.NONE;
    }

    /**
     * 构造分页解析结果，并统一处理边界规范化和空结果判断。
     */
    private PageWhereResult buildPageResult(RowNumberColumnType columnType, Long ge, Long le) {
        Long normalizedGe = ge == null ? null : Math.max(ge, 1L);
        boolean emptyResult = (columnType == RowNumberColumnType.RAW_ROWNUM && normalizedGe != null && normalizedGe > 1)
                || (le != null && le < 1)
                || invalidRange(normalizedGe, le);
        return PageWhereResult.page(null, normalizedGe, le, emptyResult);
    }

    private Expression buildAndExpression(Expression leftExpression, Expression rightExpression) {
        if (leftExpression == null) {
            return rightExpression;
        }
        if (rightExpression == null) {
            return leftExpression;
        }
        return com.sangsang.util.ExpressionsUtil.buildAndExpression(leftExpression, rightExpression);
    }

    private boolean invalidRange(Long ge, Long le) {
        return ge != null && le != null && ge > le;
    }

    private Long max(Long left, Long right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }

    private Long min(Long left, Long right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.min(left, right);
    }

    /**
     * 行号字段类型。
     */
    private enum RowNumberColumnType {
        NONE,
        RAW_ROWNUM,
        ROW_NUMBER_ALIAS
    }
}
