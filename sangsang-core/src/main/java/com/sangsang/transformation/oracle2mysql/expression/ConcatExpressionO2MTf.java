package com.sangsang.transformation.oracle2mysql.expression;

import com.sangsang.transformation.ExpressionTransformation;
import com.sangsang.util.ExpressionsUtil;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.arithmetic.Concat;

/**
 * 'aaa'||'bbb' ===> concat('aaa', 'bbb')
 *
 * @author liutangqi
 * @date 2026/4/7 13:43
 */
public class ConcatExpressionO2MTf extends ExpressionTransformation {
    @Override
    public boolean needTransformation(Expression expression) {
        return expression instanceof Concat;
    }

    @Override
    public Expression doTransformation(Expression expression) {
        Concat concat = (Concat) expression;
        Expression leftExpression = concat.getLeftExpression();
        Expression rightExpression = concat.getRightExpression();
        Function function = new Function();
        function.setName("CONCAT");
        function.setParameters(ExpressionsUtil.buildExpressionList(leftExpression, rightExpression));
        return function;
    }
}
