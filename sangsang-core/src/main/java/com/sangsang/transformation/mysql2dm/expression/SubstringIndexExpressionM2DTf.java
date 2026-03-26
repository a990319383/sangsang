package com.sangsang.transformation.mysql2dm.expression;

import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.transformation.ExpressionTransformation;
import com.sangsang.util.StringUtils;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.arithmetic.Addition;
import net.sf.jsqlparser.expression.operators.arithmetic.Subtraction;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;

import java.util.Arrays;
import java.util.List;

/**
 * SUBSTRING_INDEX函数转换
 * 这个函数比较复杂，需要进行复杂的拼接
 * SUBSTRING_INDEX(str,delim,count) ===>
 * CASE
 * ----WHEN count = 0 THEN ''
 * ----WHEN count > 0 THEN
 * ------CASE
 * --------WHEN INSTR(str, delim, 1, count) = 0 THEN str
 * --------ELSE SUBSTR(str, 1, INSTR(str, delim, 1, count) - 1)
 * ------END
 * ----ELSE
 * ------CASE
 * --------WHEN INSTR(str, delim, -1, ABS(count)) = 0 THEN str
 * --------ELSE SUBSTR(str, INSTR(str, delim, -1, ABS(count)) + LENGTH(delim))
 * ------END
 * END
 *
 * @author liutangqi
 * @date 2025/6/5 16:30
 */
public class SubstringIndexExpressionM2DTf extends ExpressionTransformation {
    @Override
    public boolean needTransformation(Expression expression) {
        return expression instanceof Function
                && "SUBSTRING_INDEX".equalsIgnoreCase(((Function) expression).getName());
    }

    @Override
    public Expression doTransformation(Expression expression) {
        Function function = (Function) expression;
        ExpressionList<Expression> parameters = (ExpressionList<Expression>) function.getParameters();
        //1.参数有误直接返回
        if (parameters.size() != 3) {
            return function;
        }

        //2.获取到核心的参数
        Expression str = parameters.get(0);
        Expression delim = parameters.get(1);
        Expression count = parameters.get(2);

        //3.开始拼凑case when
        return buildExpression(str, delim, count);
    }

    /**
     * 构建最终的函数
     *
     * @author liutangqi
     * @date 2025/6/5 17:47
     * @Param [str, delim, count]
     **/
    private CaseExpression buildExpression(Expression str, Expression delim, Expression count) {
        //1.第一个when count = 0 then ''
        //1.1 when
        EqualsTo countZeroCondition1 = buildEqualsTo(count, NumberConstant.ZERO_LONG_VALUE);
        //1.2 then
        StringValue thenExp1 = new StringValue("");
        WhenClause whenClause1 = buildWhenClause(countZeroCondition1, thenExp1);

        //2.第二个When count>0 THEN ...
        //2.1 when
        GreaterThan greaterThanWhen2 = buildGreaterThan(count, NumberConstant.ZERO_LONG_VALUE);
        //2.2 then
        Expression instr22 = buildInstr(str, delim, NumberConstant.ONE_LONG_VALUE, count);
        Subtraction subtraction22 = buildSubtraction(instr22, NumberConstant.ONE_LONG_VALUE);
        Function elseFun22 = buildSubstr(str, NumberConstant.ONE_LONG_VALUE, subtraction22);
        CaseExpression then2Exp = buildCaseExpression(buildEqualsTo(instr22, NumberConstant.ZERO_LONG_VALUE), str, elseFun22);
        WhenClause whenClause2 = buildWhenClause(greaterThanWhen2, then2Exp);

        //3.else
        Function instr3 = buildInstr(str, delim, NumberConstant.NEGATIVE_ONE_LONG_VALUE, buildAbs(count));
        Function substr3 = buildSubstr(str, buildAddition(instr3, buildLength(delim)));
        CaseExpression elseExp3 = buildCaseExpression(buildEqualsTo(instr3, NumberConstant.ZERO_LONG_VALUE), str, substr3);

        return buildCaseExpression(Arrays.asList(whenClause1, whenClause2), elseExp3);
    }

    /**
     * 构建单个case when else
     *
     * @author liutangqi
     * @date 2025/6/5 17:45
     * @Param [whenExp, thenExp, elseExp]
     **/
    private CaseExpression buildCaseExpression(Expression whenExp, Expression thenExp, Expression elseExp) {
        WhenClause whenClause = buildWhenClause(whenExp, thenExp);
        return buildCaseExpression(Arrays.asList(whenClause), elseExp);
    }

    /**
     * 构建有多个条件的case when else
     *
     * @author liutangqi
     * @date 2025/6/5 17:49
     * @Param [whenClauses, elseExp]
     **/
    private CaseExpression buildCaseExpression(List<WhenClause> whenClauses, Expression elseExp) {
        CaseExpression caseExpression = new CaseExpression();
        caseExpression.setWhenClauses(whenClauses);
        caseExpression.setElseExpression(elseExp);
        return caseExpression;
    }

    /**
     * 构建WhenClause
     *
     * @author liutangqi
     * @date 2025/6/6 14:03
     * @Param [whenExp, thenExp]
     **/
    private WhenClause buildWhenClause(Expression whenExp, Expression thenExp) {
        WhenClause whenClause = new WhenClause();
        whenClause.setWhenExpression(whenExp);
        whenClause.setThenExpression(thenExp);
        return whenClause;
    }

    /**
     * 构建instr函数
     *
     * @author liutangqi
     * @date 2025/6/6 11:26
     * @Param [str, delim, count, direction]
     **/
    private Function buildInstr(Expression str, Expression delim, LongValue direction, Expression count) {
        // INSTR(str, delim, direction,count)
        Function instrFunction = new Function();
        instrFunction.setName("INSTR");
        instrFunction.setParameters(new ExpressionList<>(Arrays.asList(str, delim, direction, count)));
        return instrFunction;
    }

    /**
     * 构建 SUBSTR 函数
     *
     * @author liutangqi
     * @date 2025/6/6 13:30
     * @Param [str, start, length]
     **/
    private Function buildSubstr(Expression str, Expression start, Expression length) {
        Function substrFunction = new Function();
        substrFunction.setName("SUBSTR");
        substrFunction.setParameters(new ExpressionList<>(Arrays.asList(str, start, length)));
        return substrFunction;
    }

    /**
     * 构建 SUBSTR 函数
     *
     * @author liutangqi
     * @date 2025/6/6 13:30
     * @Param [str, start, length]
     **/
    private Function buildSubstr(Expression str, Expression start) {
        Function substrFunction = new Function();
        substrFunction.setName("SUBSTR");
        substrFunction.setParameters(new ExpressionList<>(Arrays.asList(str, start)));
        return substrFunction;
    }

    /**
     * 构建减法
     *
     * @author liutangqi
     * @date 2025/6/6 13:40
     * @Param [left, right]
     **/
    private Subtraction buildSubtraction(Expression left, Expression right) {
        Subtraction subtraction = new Subtraction();
        subtraction.setLeftExpression(left);
        subtraction.setRightExpression(right);
        return subtraction;
    }

    /**
     * 构建加法
     *
     * @author liutangqi
     * @date 2025/6/6 13:46
     * @Param [left, right]
     **/
    private Addition buildAddition(Expression left, Expression right) {
        Addition addition = new Addition();
        addition.setLeftExpression(left);
        addition.setRightExpression(right);
        return addition;
    }

    /**
     * 构建EqualsTo函数
     *
     * @author liutangqi
     * @date 2025/6/6 11:29
     * @Param [left, right]
     **/
    private EqualsTo buildEqualsTo(Expression left, Expression right) {
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(left);
        equalsTo.setRightExpression(right);
        return equalsTo;
    }

    /**
     * 构建大于函数
     *
     * @author liutangqi
     * @date 2025/6/6 13:21
     * @Param [left, right]
     **/
    private GreaterThan buildGreaterThan(Expression left, Expression right) {
        GreaterThan greaterThan = new GreaterThan();
        greaterThan.setLeftExpression(left);
        greaterThan.setRightExpression(right);
        return greaterThan;
    }

    /**
     * 构建小于函数
     *
     * @author liutangqi
     * @date 2025/6/6 13:21
     * @Param [left, right]
     **/
    private MinorThan buidlMinorThan(Expression left, Expression right) {
        MinorThan minorThan = new MinorThan();
        minorThan.setLeftExpression(left);
        minorThan.setRightExpression(right);
        return minorThan;
    }

    /**
     * 构建ABS函数
     *
     * @author liutangqi
     * @date 2025/6/6 13:52
     * @Param [value]
     **/
    private Function buildAbs(Expression value) {
        Function absFun = new Function();
        absFun.setName("ABS");
        absFun.setParameters(new ExpressionList<>(value));
        return absFun;
    }

    /**
     * 构建LENGTH函数
     *
     * @author liutangqi
     * @date 2025/6/6 13:52
     * @Param [value]
     **/
    private Function buildLength(Expression expression) {
        Function lengthFun = new Function();
        lengthFun.setName("LENGTH");
        lengthFun.setParameters(new ExpressionList<>(expression));
        return lengthFun;
    }
}
