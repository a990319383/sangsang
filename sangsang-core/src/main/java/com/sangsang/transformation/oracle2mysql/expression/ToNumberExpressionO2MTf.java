package com.sangsang.transformation.oracle2mysql.expression;

import net.sf.jsqlparser.expression.Expression;
import com.sangsang.transformation.ExpressionTransformation;
import net.sf.jsqlparser.expression.CastExpression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.statement.create.table.ColDataType;

/**
 * TO_NUMBER 转 CAST。
 *
 * <p>默认转成 CAST(x AS SIGNED)。</p>
 * <p>如果传入的格式串中明显包含小数点，则转成 CAST(x AS DECIMAL)。</p>
 *
 * @author liutangqi && codex && gpt-5.4
 * @date 2026/3/24 18:30
 */
public class ToNumberExpressionO2MTf extends ExpressionTransformation {
    @Override
    public boolean needTransformation(Expression expression) {
        return expression instanceof Function
                && "TO_NUMBER".equalsIgnoreCase(((Function) expression).getName());
    }

    @Override
    public Expression doTransformation(Expression expression) {
        Function function = (Function) expression;
        ExpressionList<Expression> parameters = (ExpressionList<Expression>) function.getParameters();
        if (parameters == null || parameters.isEmpty()) {
            return expression;
        }

        CastExpression castExpression = new CastExpression();
        castExpression.setLeftExpression(parameters.get(0));
        castExpression.setColDataType(resolveCastType(parameters));
        return castExpression;
    }

    private ColDataType resolveCastType(ExpressionList<Expression> parameters) {
        if (parameters.size() > 1 && parameters.get(1) instanceof StringValue
                && ((StringValue) parameters.get(1)).getValue().contains(".")) {
            return new ColDataType("DECIMAL");
        }
        return new ColDataType("SIGNED");
    }
}
