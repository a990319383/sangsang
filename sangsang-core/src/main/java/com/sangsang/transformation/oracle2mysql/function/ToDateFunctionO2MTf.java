package com.sangsang.transformation.oracle2mysql.function;

import com.sangsang.domain.enums.TransformationDateFormatEnum;
import com.sangsang.transformation.FunctionTransformation;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;

import java.util.Arrays;

/**
 * TO_DATE(str, format) 转 STR_TO_DATE(str, format)。
 *
 * @author liutangqi && codex && gpt-5.4
 * @date 2026/3/24 18:30
 */
public class ToDateFunctionO2MTf extends FunctionTransformation {
    @Override
    public boolean needTransformation(Function function) {
        return "TO_DATE".equalsIgnoreCase(function.getName());
    }

    @Override
    public Function doTransformation(Function function) {
        ExpressionList<Expression> parameters = (ExpressionList<Expression>) function.getParameters();
        if (parameters == null || parameters.size() != 2) {
            return function;
        }

        Expression valueExp = parameters.get(0);
        Expression formatExp = parameters.get(1);
        String mysqlFormat = TransformationDateFormatEnum.oracle2mysqlFormat(formatExp.toString());

        function.setName("STR_TO_DATE");
        function.setParameters(new ExpressionList<>(Arrays.asList(valueExp, new StringValue(mysqlFormat))));
        return function;
    }
}
