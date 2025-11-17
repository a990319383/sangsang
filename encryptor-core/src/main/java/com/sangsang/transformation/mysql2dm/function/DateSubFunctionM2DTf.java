package com.sangsang.transformation.mysql2dm.function;

import com.sangsang.transformation.FunctionTransformation;
import com.sangsang.util.StringUtils;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.IntervalExpression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;

/**
 * DATE_SUB函数转换为DATEADD
 * 栗子：DATE_SUB(NOW(), INTERVAL 30 DAY) ==> DATEADD(DAY, -30, NOW())
 *
 * @author liutangqi
 * @date 2025/5/21 15:32
 */
public class DateSubFunctionM2DTf extends FunctionTransformation {
    @Override
    public boolean needTransformation(Function function) {
        return "DATE_SUB".equalsIgnoreCase(function.getName());
    }

    @Override
    public Function doTransformation(Function function) {
        ExpressionList<?> parameters = function.getParameters();
        //1.参数有误直接返回
        if (parameters.size() != 2 || !(parameters.get(1) instanceof IntervalExpression)) {
            return function;
        }

        //2.获取核心的几个参数 栗子： DATE_SUB(NOW(), INTERVAL 30 DAY) ==> DATEADD(DAY, -30, NOW())
        IntervalExpression intervalExpression = (IntervalExpression) parameters.get(1);
        //数量（栗子中的30）
        String parameter = intervalExpression.getParameter();
        //单位（栗子中的DAY）
        String intervalType = intervalExpression.getIntervalType();
        //时间（栗子中的NOW()）
        Expression timeExp = parameters.get(0);

        //3.拼凑新的表达式
        Function dateAddFunction = new Function();
        dateAddFunction.setName("DATEADD");
        ExpressionList<Expression> dateAddParameters = new ExpressionList();
        dateAddParameters.add(new Column(intervalType));
        //注意：从时间减变成时间加，这里时间需要取个相反数
        dateAddParameters.add(new LongValue(Math.negateExact(Integer.valueOf(parameter))));
        dateAddParameters.add(timeExp);
        dateAddFunction.setParameters(dateAddParameters);
        return dateAddFunction;
    }
}
