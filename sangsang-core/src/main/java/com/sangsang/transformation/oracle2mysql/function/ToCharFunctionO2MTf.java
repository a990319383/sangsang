package com.sangsang.transformation.oracle2mysql.function;

import com.sangsang.domain.enums.TransformationDateFormatEnum;
import com.sangsang.transformation.FunctionTransformation;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;

import java.util.Arrays;

/**
 * TO_CHAR(date, format) 转 DATE_FORMAT(date, format)。
 *
 * @author liutangqi && codex && gpt-5.4
 * @date 2026/3/24 18:30
 */
public class ToCharFunctionO2MTf extends FunctionTransformation {
    @Override
    public boolean needTransformation(Function function) {
        return "TO_CHAR".equalsIgnoreCase(function.getName());
    }

    @Override
    public Function doTransformation(Function function) {
        ExpressionList<Expression> parameters = (ExpressionList<Expression>) function.getParameters();
        if (parameters == null || parameters.size() != 2) {
            return function;
        }

        Expression dateExp = parameters.get(0);
        Expression formatExp = parameters.get(1);
        String mysqlFormat = TransformationDateFormatEnum.oracle2mysqlFormat(formatExp.toString());

        function.setName("DATE_FORMAT");
        function.setParameters(new ExpressionList<>(Arrays.asList(dateExp, new StringValue(mysqlFormat))));
        return function;
    }
}
