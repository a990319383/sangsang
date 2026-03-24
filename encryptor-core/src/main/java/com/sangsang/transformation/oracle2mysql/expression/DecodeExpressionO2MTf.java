package com.sangsang.transformation.oracle2mysql.expression;

import com.sangsang.transformation.ExpressionTransformation;
import net.sf.jsqlparser.expression.CaseExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.WhenClause;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;

import java.util.ArrayList;
import java.util.List;

/**
 * DECODE 转 CASE。
 *
 * <p>示例：</p>
 * <p>DECODE(a, 1, 'x', 'y') -> CASE a WHEN 1 THEN 'x' ELSE 'y' END</p>
 *
 * @author liutangqi && codex && gpt-5.4
 * @date 2026/3/24 18:30
 */
public class DecodeExpressionO2MTf extends ExpressionTransformation {
    @Override
    public boolean needTransformation(Expression expression) {
        return expression instanceof Function
                && "DECODE".equalsIgnoreCase(((Function) expression).getName());
    }

    @Override
    public Expression doTransformation(Expression expression) {
        Function function = (Function) expression;
        ExpressionList<Expression> parameters = (ExpressionList<Expression>) function.getParameters();
        if (parameters == null || parameters.size() < 3) {
            return expression;
        }

        Expression switchExpression = parameters.get(0);
        List<WhenClause> whenClauses = new ArrayList<>();
        int pairEndIndex = parameters.size() - 1;
        Expression elseExpression = new NullValue();

        // 参数总数为偶数时，最后一个参数为 default。
        if (parameters.size() % 2 == 0) {
            elseExpression = parameters.get(parameters.size() - 1);
        } else {
            pairEndIndex = parameters.size();
        }

        for (int i = 1; i < pairEndIndex; i += 2) {
            WhenClause whenClause = new WhenClause();
            whenClause.setWhenExpression(parameters.get(i));
            whenClause.setThenExpression(parameters.get(i + 1));
            whenClauses.add(whenClause);
        }

        CaseExpression caseExpression = new CaseExpression();
        caseExpression.setSwitchExpression(switchExpression);
        caseExpression.setWhenClauses(whenClauses);
        caseExpression.setElseExpression(elseExpression);
        return caseExpression;
    }
}
