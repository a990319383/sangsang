package com.sangsang.visitor.pojoencrtptor;

import com.sangsang.domain.constants.FieldConstant;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.PlaceholderFieldParseTable;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析中#{}条件入参条件所属的表以及字段
 *
 * @author liutangqi
 * @date 2024/7/11 10:47
 */
public class PlaceholderExpressionVisitor extends PlaceholderFieldParseTable implements ExpressionVisitor {

    /**
     * 当此表达式和上游表达式相关联时，这个是传的上游的表达式
     * 例如：  case 字段 when xxx    的when语句的时候，需要知道这个when语句所属的case后面的字段，这个变量是存储这种情况的case字段的
     */
    private Expression upstreamExpression;

    /**
     * 获取当前层解析对象
     *
     * @author liutangqi
     * @date 2025/3/5 10:40
     * @Param [placeholderFieldParseTable]
     **/
    public static PlaceholderExpressionVisitor newInstanceCurLayer(PlaceholderFieldParseTable placeholderFieldParseTable, Expression upstreamExpression) {
        return new PlaceholderExpressionVisitor(placeholderFieldParseTable, placeholderFieldParseTable.getPlaceholderColumnTableMap(), upstreamExpression);
    }

    /**
     * 获取当前层解析对象
     *
     * @author liutangqi
     * @date 2025/3/5 10:40
     * @Param [placeholderFieldParseTable]
     **/
    public static PlaceholderExpressionVisitor newInstanceCurLayer(PlaceholderFieldParseTable placeholderFieldParseTable) {
        return new PlaceholderExpressionVisitor(placeholderFieldParseTable, placeholderFieldParseTable.getPlaceholderColumnTableMap(), null);
    }

    /**
     * 获取当前层解析对象
     *
     * @author liutangqi
     * @date 2025/3/5 10:40
     * @Param [placeholderFieldParseTable]
     **/
    public static PlaceholderExpressionVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable, Map<String, ColumnTableDto> placeholderColumnTableMap) {
        return new PlaceholderExpressionVisitor(baseFieldParseTable, placeholderColumnTableMap, null);
    }

    private PlaceholderExpressionVisitor(BaseFieldParseTable baseFieldParseTable, Map<String, ColumnTableDto> placeholderColumnTableMap, Expression upstreamExpression) {
        super(baseFieldParseTable, placeholderColumnTableMap);
        this.upstreamExpression = upstreamExpression;
    }

    public Expression getUpstreamExpression() {
        return upstreamExpression;
    }

    @Override
    public void visit(BitwiseRightShift bitwiseRightShift) {

    }

    @Override
    public void visit(BitwiseLeftShift bitwiseLeftShift) {

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

    /**
     * 括起来的一堆条件
     *
     * @author liutangqi
     * @date 2024/7/12 14:41
     * @Param [parenthesis]
     **/
    @Override
    public void visit(Parenthesis parenthesis) {
        //解析括号括起来的表达式
        Expression exp = parenthesis.getExpression();
        exp.accept(this);
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
    public void visit(IntegerDivision integerDivision) {

    }

    @Override
    public void visit(Multiplication multiplication) {

    }

    @Override
    public void visit(Subtraction subtraction) {

    }

    @Override
    public void visit(AndExpression andExpression) {
        //pojo模式处理左右表达式
        JsqlparserUtil.visitPojoBinaryExpression(this, andExpression);
    }

    @Override
    public void visit(OrExpression orExpression) {
        //pojo模式处理左右表达式
        JsqlparserUtil.visitPojoBinaryExpression(this, orExpression);

    }

    @Override
    public void visit(XorExpression xorExpression) {
        //pojo模式处理左右表达式
        JsqlparserUtil.visitPojoBinaryExpression(this, xorExpression);
    }

    @Override
    public void visit(Between between) {

    }

    @Override
    public void visit(OverlapsCondition overlapsCondition) {

    }

    /**
     * 左右表达式，当有一边表达式是 我们替换的占位符，有一边是Column，则将他们对应关系维护进结果集中
     *
     * @author liutangqi
     * @date 2024/7/11 11:08
     * @Param [equalsTo]
     **/
    @Override
    public void visit(EqualsTo equalsTo) {
        //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
        JsqlparserUtil.parseWhereColumTable(this,
                equalsTo,
                this.getPlaceholderColumnTableMap());
    }

    @Override
    public void visit(GreaterThan greaterThan) {

    }

    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {

    }


    /**
     * in的处理比较复杂，主要将左右两边表达式进行匹配，处理其中的占位符信息
     * 语法1： xxx in (?,?)
     * 语法2： xxx in (select xxx from )
     * 语法3： ? in (select xxx from)
     * 语法4： (xxx,yyy) in ((?,?),(?,?))
     * 语法5： (xxx,yyy) in (select xxx,yyy from )
     * 语法6： concat("aaa",tu.phone) in (? , ?)
     * 语法7： (?,?) in (select xxx,yyy from )
     *
     * @author liutangqi
     * @date 2025/3/17 14:49
     * @Param [inExpression]
     **/
    @Override
    public void visit(InExpression inExpression) {
        Expression leftExpression = inExpression.getLeftExpression();
        Expression rightExpression = inExpression.getRightExpression();
        List<Expression> leftExpressionList = new ArrayList<>();

        //1.记录左边的表达式,用于后面右边和左边对应时，解析占位符
        //1.1 左边是单列的常量或者是字段列时（对应语法1，语法2，语法3）
        if ((leftExpression instanceof Column) || (inExpression.getLeftExpression() instanceof JdbcParameter)) {
            leftExpressionList.add(leftExpression);
        }
        //1.2 左边是多值字段时（对应语法4，语法5）
        else if (leftExpression instanceof ParenthesedExpressionList) {
            ParenthesedExpressionList<Expression> leftParenthesedExpressionList = (ParenthesedExpressionList<Expression>) leftExpression;
            for (Expression expression : leftParenthesedExpressionList) {
                leftExpressionList.add(expression);
            }
        }
        //1.3 左边是其它情况时（对应语法6）
        else {
            //这种情况不做处理，这种情况#{}占位符所属的字段信息是一个聚合结果，同时来源多张表，不支持此种写法，两个单独的字段聚合后，单独加密和整体加密密文肯定不同
            // 写出这种sql的时候请反省一下自己，表结构是不是有问题，硬要用这种写法的，请使用数据库函数加密的db模式
        }

        //2.解析右边的表达式，和左边做对应，解析对应的占位符信息
        //2.1 当右边是多列，并且右边也是多列的集合时（对应语法4）
        if ((rightExpression instanceof ParenthesedExpressionList) && (((ParenthesedExpressionList) rightExpression).get(0)) instanceof ExpressionList) {
            ParenthesedExpressionList<ExpressionList> rightExpressionList = (ParenthesedExpressionList<ExpressionList>) rightExpression;
            for (ExpressionList<Expression> expList : rightExpressionList) {
                for (int i = 0; i < expList.size(); i++) {
                    //找出对应的左边的表达式
                    Expression leftExp = leftExpressionList.get(i);
                    //解析占位符
                    JsqlparserUtil.parseWhereColumTable(this,
                            leftExp,
                            expList.get(i),
                            this.getPlaceholderColumnTableMap());
                }
            }
        }
        //2.2 当右边是多列的其它情况（对应语法1,语法6）注意：此时左边肯定只有1列，所以左边如果存在密文存储的字段，则右边全部都需要处理
        else if ((rightExpression instanceof ParenthesedExpressionList)) {
            ParenthesedExpressionList<Expression> rightExpressionList = (ParenthesedExpressionList<Expression>) rightExpression;
            for (int i = 0; i < rightExpressionList.size(); i++) {
                //找出对应的左边的表达式（语法1中左表达式集合长度肯定为1，所以get(0)，语法6这种不兼容，所以直接返回null）
                Expression leftExp = CollectionUtils.isNotEmpty(leftExpressionList) ? leftExpressionList.get(0) : null;
                //解析占位符
                JsqlparserUtil.parseWhereColumTable(this,
                        leftExp,
                        rightExpressionList.get(i),
                        this.getPlaceholderColumnTableMap());
            }
        }
        //2.3 当右边是子查询时 （对应语法2，语法3，语法5 ）
        else if (rightExpression instanceof ParenthesedSelect) {
            ParenthesedSelect rightSelect = (ParenthesedSelect) rightExpression;
            //这种情况右边是一个完全独立的sql，单独解析   注意：子查询是有权限读取上游表字段信息的，所以这里单独解析的时候将上游的解析结果合并到下游
            FieldParseParseTableSelectVisitor fPTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceIndividualMap(this);
            rightSelect.accept(fPTableSelectVisitor);
            //用这个解析的结果集解析where后面的占位符
            PlaceholderSelectVisitor placeholderSelectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(fPTableSelectVisitor,
                    this.getPlaceholderColumnTableMap(),
                    leftExpressionList);
            rightSelect.accept(placeholderSelectVisitor);
        }
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
        //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
        JsqlparserUtil.parseWhereColumTable(this,
                likeExpression,
                this.getPlaceholderColumnTableMap());
    }

    @Override
    public void visit(MinorThan minorThan) {

    }

    @Override
    public void visit(MinorThanEquals minorThanEquals) {

    }

    @Override
    public void visit(NotEqualsTo notEqualsTo) {
        //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
        JsqlparserUtil.parseWhereColumTable(this,
                notEqualsTo,
                this.getPlaceholderColumnTableMap());
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
    public void visit(Column column) {
        //当上游字段不为空时，说明这个列和上游字段是相互对应的，所以处理他们的占位符对应关系
        if (this.upstreamExpression != null) {
            JsqlparserUtil.parseWhereColumTable(this,
                    this.upstreamExpression,
                    column,
                    this.getPlaceholderColumnTableMap());
        }
    }

    /**
     * 子查询
     * 当exist时会走子查询的逻辑
     *
     * @author liutangqi
     * @date 2024/7/12 10:18
     * @Param [subSelect]
     **/
    @Override
    public void visit(Select subSelect) {
        //注意：exist这种情况，层数不需要加1，这里使用的字段和上级是同一层的
        subSelect.accept(PlaceholderSelectVisitor.newInstanceCurLayer(this));
    }

    /**
     * case 字段 when xxx then
     * case when 字段=xxx then
     * 只有下面情况的占位符才有字段对应，需要进行处理
     * 情况1: case 表字段  when ?占位符 then xxx
     * 情况2: case when 表字段=? then （这种情况条件在when里面）
     * 情况3：... then 表字段>= ?占位符
     *
     * @author liutangqi
     * @date 2024/7/31 10:58
     * @Param [caseExpression]
     **/
    @Override
    public void visit(CaseExpression caseExpression) {
        //记录当前的case 后面的所属字段，如果when 语句后面是表达式的话，需要知道case后面的所属字段，才知道是否需要加密
        Expression upstreamExpression = caseExpression.getSwitchExpression();

        //处理when条件
        if (CollectionUtils.isNotEmpty(caseExpression.getWhenClauses())) {
            for (WhenClause whenClause : caseExpression.getWhenClauses()) {
                //这里处理的逻辑在下面的public void visit(WhenClause whenClause)会处理
                PlaceholderExpressionVisitor placeholderWhereExpressionVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(this, upstreamExpression);
                whenClause.accept(placeholderWhereExpressionVisitor);
            }
        }

        //else条件 只用处理else中是 表达式的场景  栗子：  case 字段 when xxx then  字段 >= ?占位符  else 字段 >= ?占位符，只有这种情况下，占位符才有对应的表字段信息
        Expression elseExpression = caseExpression.getElseExpression();
        if (elseExpression instanceof BinaryExpression) {
            //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
            JsqlparserUtil.parseWhereColumTable(this,
                    (BinaryExpression) elseExpression,
                    this.getPlaceholderColumnTableMap());
        }

    }

    /**
     * 上面case when 中的when语句会走这里
     *
     * @author liutangqi
     * @date 2024/7/30 17:35
     * @Param [whenClause]
     **/
    @Override
    public void visit(WhenClause whenClause) {
        //对应case when的情况1： case 表字段  when ?占位符 then xxx
        if (this.upstreamExpression instanceof Column && whenClause.getWhenExpression().toString().contains(FieldConstant.PLACEHOLDER)) {
            ColumnTableDto columnTableDto = JsqlparserUtil.parseColumn((Column) this.upstreamExpression, this);
            this.getPlaceholderColumnTableMap().put(whenClause.getWhenExpression().toString(), columnTableDto);
        }

        //对应case when 的情况2： case  when 表字段=?占位符 then
        if (whenClause.getWhenExpression() instanceof BinaryExpression) {
            //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
            JsqlparserUtil.parseWhereColumTable(this,
                    (BinaryExpression) whenClause.getWhenExpression(),
                    this.getPlaceholderColumnTableMap());
        }

        //对应case when 的情况3  ：... then 表字段>= ?占位符
        Expression thenExpression = whenClause.getThenExpression();
        if (thenExpression instanceof BinaryExpression) {
            //如果有一边表达式是 特殊的占位符，则维护占位符对应的表字段信息
            JsqlparserUtil.parseWhereColumTable(this,
                    (BinaryExpression) thenExpression,
                    this.getPlaceholderColumnTableMap());
        }


    }

    @Override
    public void visit(ExistsExpression existsExpression) {
        //exist 这里会走 SubSelect 子查询的逻辑
        existsExpression.getRightExpression().accept(this);
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
    public void visit(CastExpression castExpression) {

    }

    @Override
    public void visit(Modulo modulo) {

    }

    @Override
    public void visit(AnalyticExpression analyticExpression) {

    }

    @Override
    public void visit(ExtractExpression extractExpression) {

    }

    @Override
    public void visit(IntervalExpression intervalExpression) {

    }

    @Override
    public void visit(OracleHierarchicalExpression oracleHierarchicalExpression) {

    }

    @Override
    public void visit(RegExpMatchOperator regExpMatchOperator) {

    }

    @Override
    public void visit(JsonExpression jsonExpression) {

    }

    @Override
    public void visit(JsonOperator jsonOperator) {

    }


    @Override
    public void visit(UserVariable userVariable) {

    }

    @Override
    public void visit(NumericBind numericBind) {

    }

    @Override
    public void visit(KeepExpression keepExpression) {

    }

    @Override
    public void visit(MySQLGroupConcat mySQLGroupConcat) {

    }

    @Override
    public void visit(ExpressionList<?> expressionList) {

    }


    @Override
    public void visit(RowConstructor rowConstructor) {

    }

    @Override
    public void visit(RowGetExpression rowGetExpression) {

    }

    @Override
    public void visit(OracleHint oracleHint) {

    }

    @Override
    public void visit(TimeKeyExpression timeKeyExpression) {

    }

    @Override
    public void visit(DateTimeLiteralExpression dateTimeLiteralExpression) {

    }

    @Override
    public void visit(NotExpression notExpression) {

    }

    @Override
    public void visit(NextValExpression nextValExpression) {

    }

    @Override
    public void visit(CollateExpression collateExpression) {

    }

    @Override
    public void visit(SimilarToExpression similarToExpression) {

    }

    @Override
    public void visit(ArrayExpression arrayExpression) {

    }

    @Override
    public void visit(ArrayConstructor arrayConstructor) {

    }

    @Override
    public void visit(VariableAssignment variableAssignment) {

    }

    @Override
    public void visit(XMLSerializeExpr xmlSerializeExpr) {

    }

    @Override
    public void visit(TimezoneExpression timezoneExpression) {

    }

    @Override
    public void visit(JsonAggregateFunction jsonAggregateFunction) {

    }

    @Override
    public void visit(JsonFunction jsonFunction) {

    }

    @Override
    public void visit(ConnectByRootOperator connectByRootOperator) {

    }

    @Override
    public void visit(OracleNamedFunctionParameter oracleNamedFunctionParameter) {

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
