package com.sangsang.visitor.isolation;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
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
 * @author liutangqi
 * @date 2025/6/13 16:56
 */
public class IsolationExpressionVisitor extends BaseFieldParseTable implements ExpressionVisitor {
    /**
     * 获取当前层实例
     *
     * @author liutangqi
     * @date 2025/6/13 16:57
     * @Param [baseFieldParseTable]
     **/
    public static IsolationExpressionVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new IsolationExpressionVisitor(baseFieldParseTable.getLayer(), baseFieldParseTable.getLayerSelectTableFieldMap(), baseFieldParseTable.getLayerFieldTableMap());
    }

    private IsolationExpressionVisitor(int layer, Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    @Override
    public void visit(BitwiseRightShift aThis) {
        Optional.ofNullable(aThis.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(aThis.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(BitwiseLeftShift aThis) {
        Optional.ofNullable(aThis.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(aThis.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(NullValue nullValue) {

    }

    /**
     * select * from tb_user tu where tu.id = coalesce((select id from sys_user), 0)
     * 类似coalesce这种语法
     *
     * @author liutangqi
     * @date 2026/9/4 15:19
     * @Param [function]
     **/
    @Override
    public void visit(Function function) {
        Optional.ofNullable(function.getParameters()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(function.getNamedParameters()).ifPresent(p -> p.accept(this));
        if (function.getAttribute() instanceof Expression) {
            ((Expression) function.getAttribute()).accept(this);
        }
        Optional.ofNullable(function.getKeep()).ifPresent(p -> p.accept(this));
        if (function.getOrderByElements() != null) {
            function.getOrderByElements().forEach(p -> Optional.ofNullable(p.getExpression()).ifPresent(e -> e.accept(this)));
        }
    }

    @Override
    public void visit(SignedExpression signedExpression) {
        Optional.ofNullable(signedExpression.getExpression()).ifPresent(p -> p.accept(this));
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

    /**
     * 括号括起来的表达式
     * 主要处理被括起来的子查询 exist之类的表达式
     *
     * @author liutangqi
     * @date 2025/6/13 17:57
     * @Param [parenthesis]
     **/
    @Override
    public void visit(Parenthesis parenthesis) {
        Optional.ofNullable(parenthesis.getExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(StringValue stringValue) {

    }

    @Override
    public void visit(Addition addition) {
        Optional.ofNullable(addition.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(addition.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(Division division) {
        Optional.ofNullable(division.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(division.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(IntegerDivision division) {
        Optional.ofNullable(division.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(division.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(Multiplication multiplication) {
        Optional.ofNullable(multiplication.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(multiplication.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(Subtraction subtraction) {
        Optional.ofNullable(subtraction.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(subtraction.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(AndExpression andExpression) {
        Optional.ofNullable(andExpression.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(andExpression.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(OrExpression orExpression) {
        Optional.ofNullable(orExpression.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(orExpression.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(XorExpression orExpression) {
        Optional.ofNullable(orExpression.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(orExpression.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(Between between) {
        Optional.ofNullable(between.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(between.getBetweenExpressionStart()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(between.getBetweenExpressionEnd()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(OverlapsCondition overlapsCondition) {
        Optional.ofNullable(overlapsCondition.getLeft()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(overlapsCondition.getRight()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(EqualsTo equalsTo) {
        Optional.ofNullable(equalsTo.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(equalsTo.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        Optional.ofNullable(greaterThan.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(greaterThan.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {
        Optional.ofNullable(greaterThanEquals.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(greaterThanEquals.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(InExpression inExpression) {
        Optional.ofNullable(inExpression.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(inExpression.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(FullTextSearch fullTextSearch) {
        Optional.ofNullable(fullTextSearch.getMatchColumns()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(fullTextSearch.getAgainstValue()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(IsNullExpression isNullExpression) {
        Optional.ofNullable(isNullExpression.getLeftExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(IsBooleanExpression isBooleanExpression) {
        Optional.ofNullable(isBooleanExpression.getLeftExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(LikeExpression likeExpression) {
        Optional.ofNullable(likeExpression.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(likeExpression.getRightExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(likeExpression.getEscape()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(MinorThan minorThan) {
        Optional.ofNullable(minorThan.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(minorThan.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(MinorThanEquals minorThanEquals) {
        Optional.ofNullable(minorThanEquals.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(minorThanEquals.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(NotEqualsTo notEqualsTo) {
        Optional.ofNullable(notEqualsTo.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(notEqualsTo.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(DoubleAnd doubleAnd) {
        Optional.ofNullable(doubleAnd.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(doubleAnd.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(Contains contains) {
        Optional.ofNullable(contains.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(contains.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    /**
     * @> 或者 @< 这种包含的语法
     * WHERE array_col @> ARRAY[1, 2]
     * -- ContainedBy: 检查 array_col 是否被 1、2、3 所包含
     * @author liutangqi
     * @date 2026/9/4 15:27
     * @Param [containedBy]
     **/
    @Override
    public void visit(ContainedBy containedBy) {
        Optional.ofNullable(containedBy.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(containedBy.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    /**
     * 这里始终没找到可以走这里的语法
     *
     * @author liutangqi
     * @date 2026/9/4 15:29
     * @Param [selectBody]
     **/
    @Override
    public void visit(ParenthesedSelect selectBody) {
    }

    @Override
    public void visit(Column tableColumn) {

    }

    @Override
    public void visit(CaseExpression caseExpression) {
        Optional.ofNullable(caseExpression.getSwitchExpression()).ifPresent(p -> p.accept(this));
        if (caseExpression.getWhenClauses() != null) {
            for (WhenClause whenClause : caseExpression.getWhenClauses()) {
                Optional.ofNullable(whenClause).ifPresent(p -> p.accept(this));
            }
        }
        Optional.ofNullable(caseExpression.getElseExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(WhenClause whenClause) {
        Optional.ofNullable(whenClause.getWhenExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(whenClause.getThenExpression()).ifPresent(p -> p.accept(this));
    }

    /**
     * exist
     *
     * @author liutangqi
     * @date 2025/6/13 17:58
     * @Param [existsExpression]
     **/
    @Override
    public void visit(ExistsExpression existsExpression) {
        Expression rightExpression = existsExpression.getRightExpression();
        Optional.ofNullable(rightExpression).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(MemberOfExpression memberOfExpression) {
        Optional.ofNullable(memberOfExpression.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(memberOfExpression.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(AnyComparisonExpression anyComparisonExpression) {
        Optional.ofNullable(anyComparisonExpression.getSelect()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(Concat concat) {
        Optional.ofNullable(concat.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(concat.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(Matches matches) {
        Optional.ofNullable(matches.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(matches.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(BitwiseAnd bitwiseAnd) {
        Optional.ofNullable(bitwiseAnd.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(bitwiseAnd.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(BitwiseOr bitwiseOr) {
        Optional.ofNullable(bitwiseOr.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(bitwiseOr.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(BitwiseXor bitwiseXor) {
        Optional.ofNullable(bitwiseXor.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(bitwiseXor.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(CastExpression cast) {
        Optional.ofNullable(cast.getLeftExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(Modulo modulo) {
        Optional.ofNullable(modulo.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(modulo.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(AnalyticExpression aexpr) {
        Optional.ofNullable(aexpr.getExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(aexpr.getOffset()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(aexpr.getDefaultValue()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(aexpr.getPartitionExpressionList()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(aexpr.getFilterExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(aexpr.getKeep()).ifPresent(p -> p.accept(this));
        if (aexpr.getOrderByElements() != null) {
            aexpr.getOrderByElements().forEach(p -> Optional.ofNullable(p.getExpression()).ifPresent(e -> e.accept(this)));
        }
        if (aexpr.getFuncOrderBy() != null) {
            aexpr.getFuncOrderBy().forEach(p -> Optional.ofNullable(p.getExpression()).ifPresent(e -> e.accept(this)));
        }
    }

    @Override
    public void visit(ExtractExpression eexpr) {
        Optional.ofNullable(eexpr.getExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(IntervalExpression iexpr) {
        Optional.ofNullable(iexpr.getExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(OracleHierarchicalExpression oexpr) {
        Optional.ofNullable(oexpr.getStartExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(oexpr.getConnectExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(RegExpMatchOperator rexpr) {
        Optional.ofNullable(rexpr.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(rexpr.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(JsonExpression jsonExpr) {
        Optional.ofNullable(jsonExpr.getExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(JsonOperator jsonExpr) {
        Optional.ofNullable(jsonExpr.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(jsonExpr.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(UserVariable var) {

    }

    @Override
    public void visit(NumericBind bind) {

    }

    @Override
    public void visit(KeepExpression aexpr) {
        if (aexpr.getOrderByElements() != null) {
            aexpr.getOrderByElements().forEach(p -> Optional.ofNullable(p.getExpression()).ifPresent(e -> e.accept(this)));
        }
    }

    @Override
    public void visit(MySQLGroupConcat groupConcat) {
        Optional.ofNullable(groupConcat.getExpressionList()).ifPresent(p -> p.accept(this));
        if (groupConcat.getOrderByElements() != null) {
            groupConcat.getOrderByElements().forEach(p -> Optional.ofNullable(p.getExpression()).ifPresent(e -> e.accept(this)));
        }
    }

    @Override
    public void visit(ExpressionList<?> expressionList) {
        if (expressionList != null) {
            for (Expression expression : expressionList) {
                Optional.ofNullable(expression).ifPresent(p -> p.accept(this));
            }
        }
    }

    @Override
    public void visit(RowConstructor<?> rowConstructor) {
        if (rowConstructor != null) {
            for (Expression expression : rowConstructor) {
                Optional.ofNullable(expression).ifPresent(p -> p.accept(this));
            }
        }
    }

    @Override
    public void visit(RowGetExpression rowGetExpression) {
        Optional.ofNullable(rowGetExpression.getExpression()).ifPresent(p -> p.accept(this));
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
        Optional.ofNullable(aThis.getExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(NextValExpression aThis) {

    }

    @Override
    public void visit(CollateExpression aThis) {
        Optional.ofNullable(aThis.getLeftExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(SimilarToExpression aThis) {
        Optional.ofNullable(aThis.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(aThis.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(ArrayExpression aThis) {
        Optional.ofNullable(aThis.getObjExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(aThis.getIndexExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(aThis.getStartIndexExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(aThis.getStopIndexExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(ArrayConstructor aThis) {
        Optional.ofNullable(aThis.getExpressions()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(VariableAssignment aThis) {
        Optional.ofNullable(aThis.getExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(XMLSerializeExpr aThis) {
        Optional.ofNullable(aThis.getExpression()).ifPresent(p -> p.accept(this));
        if (aThis.getOrderByElements() != null) {
            aThis.getOrderByElements().forEach(p -> Optional.ofNullable(p.getExpression()).ifPresent(e -> e.accept(this)));
        }
    }

    @Override
    public void visit(TimezoneExpression aThis) {
        Optional.ofNullable(aThis.getLeftExpression()).ifPresent(p -> p.accept(this));
        if (aThis.getTimezoneExpressions() != null) {
            for (Expression expression : aThis.getTimezoneExpressions()) {
                Optional.ofNullable(expression).ifPresent(p -> p.accept(this));
            }
        }
    }

    @Override
    public void visit(JsonAggregateFunction aThis) {
        Optional.ofNullable(aThis.getExpression()).ifPresent(p -> p.accept(this));
        if (aThis.getValue() instanceof Expression) {
            ((Expression) aThis.getValue()).accept(this);
        }
        if (aThis.getExpressionOrderByElements() != null) {
            aThis.getExpressionOrderByElements().forEach(p -> Optional.ofNullable(p.getExpression()).ifPresent(e -> e.accept(this)));
        }
    }

    @Override
    public void visit(JsonFunction aThis) {
        if (aThis.getExpressions() != null) {
            for (JsonFunctionExpression expression : aThis.getExpressions()) {
                Optional.ofNullable(expression.getExpression()).ifPresent(p -> p.accept(this));
            }
        }
        if (aThis.getKeyValuePairs() != null) {
            for (JsonKeyValuePair pair : aThis.getKeyValuePairs()) {
                if (pair.getValue() instanceof Expression) {
                    ((Expression) pair.getValue()).accept(this);
                }
            }
        }
    }

    @Override
    public void visit(ConnectByRootOperator aThis) {
        Optional.ofNullable(aThis.getColumn()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(OracleNamedFunctionParameter aThis) {
        Optional.ofNullable(aThis.getExpression()).ifPresent(p -> p.accept(this));
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
        Optional.ofNullable(isDistinctExpression.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(isDistinctExpression.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(GeometryDistance geometryDistance) {
        Optional.ofNullable(geometryDistance.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(geometryDistance.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    /**
     * 场景1：
     * select
     * (select 字段 from xx )
     * from
     * 这种语法
     * 场景2：
     * xxx in (select xxx from tb)
     * 场景3：
     * exists (select xxx from tb)
     *
     * @author liutangqi
     * @date 2025/6/13 16:59
     * @Param [selectBody]
     **/
    @Override
    public void visit(Select selectBody) {
        //注意：这种语法都是单独的一个sql，这个sql解析出来的结果只有这个嵌套层才会使用，外层不会使用，并且这个嵌套层会使用外层同级的解析结果
        //1.采用独立存储空间单独解析合并当前子查询的语法
        FieldParseParseTableSelectVisitor sFieldSelectItemVisitor = FieldParseParseTableSelectVisitor.newInstanceIndividualMap(this);
        selectBody.accept(sFieldSelectItemVisitor);

        //2.利用合并后的解析结果进行语法转换处理
        IsolationSelectVisitor ilSelectVisitor = IsolationSelectVisitor.newInstanceCurLayer(sFieldSelectItemVisitor);
        selectBody.accept(ilSelectVisitor);
    }

    @Override
    public void visit(TranscodingFunction transcodingFunction) {
        Optional.ofNullable(transcodingFunction.getExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(TrimFunction trimFunction) {
        Optional.ofNullable(trimFunction.getExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(trimFunction.getFromExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(RangeExpression rangeExpression) {
        Optional.ofNullable(rangeExpression.getStartExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(rangeExpression.getEndExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(TSQLLeftJoin tsqlLeftJoin) {
        Optional.ofNullable(tsqlLeftJoin.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(tsqlLeftJoin.getRightExpression()).ifPresent(p -> p.accept(this));
    }

    @Override
    public void visit(TSQLRightJoin tsqlRightJoin) {
        Optional.ofNullable(tsqlRightJoin.getLeftExpression()).ifPresent(p -> p.accept(this));
        Optional.ofNullable(tsqlRightJoin.getRightExpression()).ifPresent(p -> p.accept(this));
    }
}
