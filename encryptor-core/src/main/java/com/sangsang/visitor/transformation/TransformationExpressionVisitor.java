package com.sangsang.visitor.transformation;

import com.sangsang.cache.transformation.TransformationInstanceCache;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.ColumnTransformationDto;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import com.sangsang.visitor.transformation.wrap.ExpressionWrapper;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.arithmetic.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.conditional.XorExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;
import java.util.stream.Collectors;


/**
 * 关于表达式的语法解析，注意，不要直接使用 expression 调用accept 来进行语法解析，使用包装类将Expression包装一层，再进行语法转换，这样会将整个Expression进行整体的语法转换
 * 正确使用方式： ExpressionWrapper.wrap(expression).accept(tfExpressionVisitor);
 *
 * @author liutangqi
 * @date 2025/5/21 15:26
 */
public class TransformationExpressionVisitor extends BaseFieldParseTable implements ExpressionVisitor {
    /**
     * 处理完成后的表达式
     * 注意：这里变量必须需要，不能去除这个设计，因为有的转换后类型不再一致，但是都属于Expression，所以不能单纯的在每个visitor中进行简单的变量重新赋值
     */
    private Expression expression;

    /**
     * 获取处理好后的表达式，获取后立马清除处理好的表达式，这样的话同一个Visitor就可以被不同的表达式复用，避免重复创建Visitor对象
     *
     * @author liutangqi
     * @date 2025/6/6 16:11
     * @Param []
     **/
    public Expression getExpression() {
        Expression res = expression;
        this.expression = null;
        return res;
    }

    /**
     * 获取当前层实例
     *
     * @author liutangqi
     * @date 2025/5/27 11:20
     * @Param [baseField]
     **/
    public static TransformationExpressionVisitor newInstanceCurLayer(BaseFieldParseTable baseField) {
        return new TransformationExpressionVisitor(baseField.getLayer(), baseField.getLayerSelectTableFieldMap(), baseField.getLayerFieldTableMap());
    }

    private TransformationExpressionVisitor(int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
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
        //1.拆解function的每个参数，将其中需要转换的参数进行语法转换
        if (function.getParameters() != null) {
            List<Expression> tfExpressions = function.getParameters().stream().map(m -> {
                //使用包装类进行转转，额外对整个Expression进行语法转换一次
                Expression tfExp = ExpressionWrapper.wrap(m).accept(this);
                return Optional.ofNullable(tfExp).orElse(m);
            }).collect(Collectors.toList());
            function.setParameters(new ExpressionList(tfExpressions));
        }

        //2.判断当前function是否需要进行语法转换
        Function tfFunction = TransformationInstanceCache.<Function>transformation(function);
        if (tfFunction != null) {
            //记录处理完成后的表达式
            this.expression = tfFunction;
        }
    }

    @Override
    public void visit(SignedExpression signedExpression) {

    }

    /**
     * update tb set xxx = ? 这种语法的?
     * 语法转换的场景不需要考虑对 ? 进行语法转换
     *
     * @author liutangqi
     * @date 2025/5/23 16:10
     * @Param [jdbcParameter]
     **/
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
     * 括号括起来的一堆条件
     *
     * @author liutangqi
     * @date 2025/5/23 16:13
     * @Param [parenthesis]
     **/
    @Override
    public void visit(Parenthesis parenthesis) {
        //依次解析每个表达式
        Expression parenthesisExpression = parenthesis.getExpression();
        //使用包装类进行转转，额外对整个Expression进行语法转换一次
        Expression tfExp = ExpressionWrapper.wrap(parenthesisExpression).accept(this);
        Optional.ofNullable(tfExp).ifPresent(p -> parenthesis.setExpression(p));
    }

    /**
     * 字符串常量
     *
     * @author liutangqi
     * @date 2025/5/23 16:41
     * @Param [stringValue]
     **/
    @Override
    public void visit(StringValue stringValue) {
        StringValue tfStringValue = TransformationInstanceCache.transformation(stringValue);
        if (tfStringValue != null) {
            //记录处理完成后的表达式
            this.expression = tfStringValue;
        }
    }

    @Override
    public void visit(Addition addition) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, addition);
    }

    @Override
    public void visit(Division division) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, division);
    }

    @Override
    public void visit(IntegerDivision division) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, division);
    }

    @Override
    public void visit(Multiplication multiplication) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, multiplication);
    }

    @Override
    public void visit(Subtraction subtraction) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, subtraction);
    }

    @Override
    public void visit(AndExpression andExpression) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, andExpression);
    }

    @Override
    public void visit(OrExpression orExpression) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, orExpression);
    }

    @Override
    public void visit(XorExpression orExpression) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, orExpression);
    }

    @Override
    public void visit(Between between) {
        Expression leftExpression = between.getLeftExpression();
        //使用包装类进行转转，额外对整个Expression进行语法转换一次
        Expression tfExpL = ExpressionWrapper.wrap(leftExpression).accept(this);
        Optional.ofNullable(tfExpL).ifPresent(p -> between.setLeftExpression(p));

    }

    @Override
    public void visit(OverlapsCondition overlapsCondition) {

    }

    /**
     * select 语句中存在 case when 字段 = xxx then 这种语法的时候， 其中字段=xxx 会走这里的解析
     * where 语句中的 = 也会走这里解析
     *
     * @author liutangqi
     * @date 2025/5/23 16:29
     * @Param [equalsTo]
     **/
    @Override
    public void visit(EqualsTo equalsTo) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, equalsTo);
    }

    /**
     * 大于
     *
     * @author liutangqi
     * @date 2025/6/4 17:31
     * @Param [greaterThan]
     **/
    @Override
    public void visit(GreaterThan greaterThan) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, greaterThan);
    }

    /**
     * 大于等于
     *
     * @author liutangqi
     * @date 2025/6/4 17:31
     * @Param [greaterThanEquals]
     **/
    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, greaterThanEquals);
    }

    /**
     * 语法1： xxx in (?,?)
     * 语法2： xxx in (select xxx from )
     * 语法3： ? in (select xxx from)
     * 语法4： (xxx,yyy) in ((?,?),(?,?))
     * 语法5： (xxx,yyy) in (select xxx,yyy from )
     * 语法6： concat("aaa",tu.phone) in (? , ?)
     * 语法7： (?,?) in (select xxx,yyy from )
     *
     * @author liutangqi
     * @date 2025/5/23 16:30
     * @Param [inExpression]
     **/
    @Override
    public void visit(InExpression inExpression) {
        //解析左表达式
        Expression leftExpression = inExpression.getLeftExpression();
        //使用包装类进行转转，额外对整个Expression进行语法转换一次
        Expression tfExpL = ExpressionWrapper.wrap(leftExpression).accept(this);
        Optional.ofNullable(tfExpL).ifPresent(p -> inExpression.setLeftExpression(p));

        //解析右表达式
        Expression rightExpression = inExpression.getRightExpression();
        //使用包装类进行转转，额外对整个Expression进行语法转换一次
        Expression tfExpR = ExpressionWrapper.wrap(rightExpression).accept(this);
        Optional.ofNullable(tfExpR).ifPresent(p -> inExpression.setRightExpression(p));
    }

    @Override
    public void visit(FullTextSearch fullTextSearch) {

    }

    @Override
    public void visit(IsNullExpression isNullExpression) {
        Expression leftExpression = isNullExpression.getLeftExpression();
        //使用包装类进行转转，额外对整个Expression进行语法转换一次
        Expression tfExp = ExpressionWrapper.wrap(leftExpression).accept(this);
        Optional.ofNullable(tfExp).ifPresent(p -> isNullExpression.setLeftExpression(p));
    }

    @Override
    public void visit(IsBooleanExpression isBooleanExpression) {
        Expression leftExpression = isBooleanExpression.getLeftExpression();
        //使用包装类进行转转，额外对整个Expression进行语法转换一次
        Expression tfExp = ExpressionWrapper.wrap(leftExpression).accept(this);
        Optional.ofNullable(tfExp).ifPresent(p -> isBooleanExpression.setLeftExpression(p));
    }

    @Override
    public void visit(LikeExpression likeExpression) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, likeExpression);
    }

    /**
     * 小于
     *
     * @author liutangqi
     * @date 2025/6/4 17:28
     * @Param [minorThan]
     **/
    @Override
    public void visit(MinorThan minorThan) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, minorThan);
    }

    /**
     * 小于等于
     *
     * @author liutangqi
     * @date 2025/6/4 17:31
     * @Param [minorThanEquals]
     **/
    @Override
    public void visit(MinorThanEquals minorThanEquals) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, minorThanEquals);
    }

    @Override
    public void visit(NotEqualsTo notEqualsTo) {
        //分别解析左右表达式
        JsqlparserUtil.visitTfBinaryExpression(this, notEqualsTo);
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
        System.out.println(selectBody);
    }

    @Override
    public void visit(Column tableColumn) {
        //1.解析当前列属于哪张表，虚拟表的也算，用于区分这个是表字段还是常量
        boolean tableFiled = JsqlparserUtil.isTableFiled(tableColumn, this.getLayer(), this.getLayerFieldTableMap());

        //2.解析这个字段是否属于真实表
        ColumnTableDto columnTableDto = JsqlparserUtil.parseColumn(tableColumn, this.getLayer(), this.getLayerFieldTableMap());

        //3.开始语法转换
        ColumnTransformationDto columnTransformationDto = TransformationInstanceCache.transformation(new ColumnTransformationDto(tableColumn, tableFiled, columnTableDto.isFromSourceTable()));
        if (columnTransformationDto != null) {
            //记录处理完成后的表达式
            this.expression = columnTransformationDto.getColumn();
        }
    }

    /**
     * case 字段 when xxx then
     * case when 字段=xxx then
     *
     * @author liutangqi
     * @date 2025/5/29 16:11
     * @Param [caseExpression]
     **/
    @Override
    public void visit(CaseExpression caseExpression) {
        //1.处理case的条件(case 字段 的时候，这个是字段)
        Expression switchExpression = caseExpression.getSwitchExpression();
        if (switchExpression != null) {
            //使用包装类进行转转，额外对整个Expression进行语法转换一次
            Expression tfExpSwitch = ExpressionWrapper.wrap(switchExpression).accept(this);
            Optional.ofNullable(tfExpSwitch).ifPresent(p -> caseExpression.setSwitchExpression(p));
        }

        //2.处理when后面的条件
        List<WhenClause> whenClauses = caseExpression.getWhenClauses();
        if (CollectionUtils.isNotEmpty(whenClauses)) {
            List<WhenClause> tfWhenClause = whenClauses.stream().map(m -> {
                //使用包装类进行转转，额外对整个Expression进行语法转换一次
                Expression tfExp = ExpressionWrapper.wrap(m).accept(this);
                //注意：这里入参是whenClause，返回的一定也是这个类型，直接强转即可(备注：如果不是这个类型的，说明转换语法有问题，整体格式都不对了)
                return (WhenClause) Optional.ofNullable(tfExp).orElse(m);
            }).collect(Collectors.toList());
            //替换原有表达式
            caseExpression.setWhenClauses(tfWhenClause);
        }

        //3.处理else
        Expression elseExpression = caseExpression.getElseExpression();
        if (elseExpression != null) {
            //使用包装类进行转转，额外对整个Expression进行语法转换一次
            Expression tfExpElse = ExpressionWrapper.wrap(elseExpression).accept(this);
            Optional.ofNullable(tfExpElse).ifPresent(p -> caseExpression.setElseExpression(p));
        }
    }

    /**
     * 上面的CaseExpression 解析出来的when的条件
     *
     * @author liutangqi
     * @date 2025/5/29 16:27
     * @Param [whenClause]
     **/
    @Override
    public void visit(WhenClause whenClause) {
        Expression thenExpression = whenClause.getThenExpression();
        if (thenExpression != null) {
            //使用包装类进行转转，额外对整个Expression进行语法转换一次
            Expression tfExpThen = ExpressionWrapper.wrap(thenExpression).accept(this);
            Optional.ofNullable(tfExpThen).ifPresent(p -> whenClause.setThenExpression(p));
        }

        Expression whenExpression = whenClause.getWhenExpression();
        if (whenExpression != null) {
            //使用包装类进行转转，额外对整个Expression进行语法转换一次
            Expression tfExpWhen = ExpressionWrapper.wrap(whenExpression).accept(this);
            Optional.ofNullable(tfExpWhen).ifPresent(p -> whenClause.setWhenExpression(p));
        }
    }

    @Override
    public void visit(ExistsExpression existsExpression) {
        Expression rightExpression = existsExpression.getRightExpression();
        //使用包装类进行转转，额外对整个Expression进行语法转换一次
        Expression tfExpR = ExpressionWrapper.wrap(rightExpression).accept(this);
        Optional.ofNullable(tfExpR).ifPresent(p -> existsExpression.setRightExpression(p));

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

    /**
     * LISTAGG(USER_NAME , ',') WITHIN GROUP (ORDER BY id DESC)   中 WITHIN GROUP这种语法
     *
     * @author liutangqi
     * @date 2025/5/30 16:12
     * @Param [aexpr]
     **/
    @Override
    public void visit(AnalyticExpression aexpr) {
        //列
        Expression exp = aexpr.getExpression();
        if (exp != null) {
            //使用包装类进行转转，额外对整个Expression进行语法转换一次
            Expression tfExp = ExpressionWrapper.wrap(exp).accept(this);
            Optional.ofNullable(tfExp).ifPresent(p -> aexpr.setExpression(p));
        }

        //order by
        List<OrderByElement> orderByElements = aexpr.getOrderByElements();
        if (CollectionUtils.isNotEmpty(orderByElements)) {
            TransformationOrderByVisitor tfOrderByVisitor = TransformationOrderByVisitor.newInstanceCurLayer(this);
            for (OrderByElement orderByElement : orderByElements) {
                orderByElement.accept(tfOrderByVisitor);
            }
        }
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
        //1.拆解groupConcat的每个参数，将其中需要转换的参数进行语法转换
        //1.1表达式
        ExpressionList<?> expressionList = groupConcat.getExpressionList();
        if (CollectionUtils.isNotEmpty(expressionList)) {
            List<Expression> tfExpressions = expressionList.stream().map(m -> {
                //使用包装类进行转转，额外对整个Expression进行语法转换一次
                Expression tfExp = ExpressionWrapper.wrap(m).accept(this);
                return Optional.ofNullable(tfExp).orElse(m);
            }).collect(Collectors.toList());
            groupConcat.setExpressionList(new ExpressionList(tfExpressions));
        }
        //1.2分隔符
        String separator = groupConcat.getSeparator();
        if (separator != null) {
            StringValue tfSeparator = TransformationInstanceCache.transformation(new StringValue(separator));
            if (tfSeparator != null) {
                //记录处理完成后的表达式
                groupConcat.setSeparator(tfSeparator.getValue());
            }
        }
        //1.3排序
        List<OrderByElement> orderByElements = groupConcat.getOrderByElements();
        if (CollectionUtils.isNotEmpty(orderByElements)) {
            TransformationOrderByVisitor tfOrderByVisitor = TransformationOrderByVisitor.newInstanceCurLayer(this);
            for (OrderByElement orderByElement : orderByElements) {
                orderByElement.accept(tfOrderByVisitor);
            }
        }
    }

    @Override
    public void visit(ExpressionList expressionList) {
        for (int i = 0; i < expressionList.size(); i++) {
            Expression exp = (Expression) expressionList.get(i);
            //使用包装类进行转转，额外对整个Expression进行语法转换一次
            Expression tfExp = ExpressionWrapper.wrap(exp).accept(this);
            expressionList.set(i, Optional.ofNullable(tfExp).orElse(exp));
        }
    }

    /**
     * 多字段 in 的时候，左边的多字段会走这里
     * where (xxx,yyy) in ((?,?),(?,?))
     *
     * @author liutangqi
     * @date 2025/6/3 13:41
     * @Param [rowConstructor]
     **/
    @Override
    public void visit(RowConstructor rowConstructor) {
        List<Expression> resExp = new ArrayList<>();

        //依次处理每个表达式
        List<Expression> expressions = (List<Expression>) rowConstructor;
        for (Expression exp : expressions) {
            //使用包装类进行转转，额外对整个Expression进行语法转换一次
            Expression tfExp = ExpressionWrapper.wrap(exp).accept(this);
            resExp.add(Optional.ofNullable(tfExp).orElse(exp));
        }

        //处理后的表达式赋值
        rowConstructor.setExpressions(resExp);
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
        Expression leftExpression = aThis.getExpression();
        //使用包装类进行转转，额外对整个Expression进行语法转换一次
        Expression tfExpL = ExpressionWrapper.wrap(leftExpression).accept(this);
        Optional.ofNullable(tfExpL).ifPresent(p -> aThis.setExpression(p));
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
     * @date 2025/5/29 17:22
     * @Param [selectBody]
     **/
    @Override
    public void visit(Select selectBody) {
        //注意：这种语法都是单独的一个sql，这个sql解析出来的结果只有这个嵌套层才会使用，外层不会使用，并且这个嵌套层会使用外层同级的解析结果
        //1.采用独立存储空间单独解析合并当前子查询的语法
        FieldParseParseTableSelectVisitor sFieldSelectItemVisitor = FieldParseParseTableSelectVisitor.newInstanceIndividualMap(this);
        selectBody.accept(sFieldSelectItemVisitor);

        //2.利用合并后的解析结果进行语法转换处理
        TransformationSelectVisitor tfSelectVisitor = TransformationSelectVisitor.newInstanceCurLayer(sFieldSelectItemVisitor);
        selectBody.accept(tfSelectVisitor);
    }

    /**
     * convert函数
     *
     * @author liutangqi
     * @date 2025/6/3 14:13
     * @Param [transcodingFunction]
     **/
    @Override
    public void visit(TranscodingFunction transcodingFunction) {
        //1.拆解每个表达式
        Expression exp = transcodingFunction.getExpression();
        if (exp != null) {
            //使用包装类进行转转，额外对整个Expression进行语法转换一次
            Expression tfExp = ExpressionWrapper.wrap(exp).accept(this);
            Optional.ofNullable(tfExp).ifPresent(p -> transcodingFunction.setExpression(p));
        }
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
