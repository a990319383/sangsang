package com.sangsang.transformation.mysql2dm.function;

import com.sangsang.domain.enums.TransformationDateFormatEnum;
import com.sangsang.transformation.FunctionTransformation;
import com.sangsang.util.StringUtils;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;

import java.util.Arrays;

/**
 * STR_TO_DATE 转换为 TO_DATE
 * STR_TO_DATE('2025-06-04 13:14:15', '%Y-%m-%d %H:%i:%s') ===> TO_DATE('2025-06-04 13:14:15', 'YYYY-MM-DD HH24:MI:SS')
 *
 * @author liutangqi
 * @date 2025/6/4 15:13
 */
public class StrToDateFunctionM2DTf extends FunctionTransformation {
    @Override
    public boolean needTransformation(Function function) {
        return "STR_TO_DATE".equalsIgnoreCase(function.getName());
    }

    @Override
    public Function doTransformation(Function function) {
        ExpressionList<Expression> parameters = (ExpressionList<Expression>) function.getParameters();
        //1.参数有误直接返回
        if (parameters.size() != 2) {
            return function;
        }

        //2.提取参数
        //栗子： STR_TO_DATE('2025-06-04 13:14:15', '%Y-%m-%d %H:%i:%s')  中的 '2025-06-04 13:14:15'
        Expression time = parameters.get(0);
        //栗子： STR_TO_DATE('2025-06-04 13:14:15', '%Y-%m-%d %H:%i:%s')  中的 '%Y-%m-%d %H:%i:%s'
        Expression format = parameters.get(1);

        //3.将format进行格式转换
        String dmFormat = TransformationDateFormatEnum.mysql2dmFormat(format.toString());

        //4.拼凑新的函数返回
        Function toDateFunction = new Function();
        toDateFunction.setName("TO_DATE");
        toDateFunction.setParameters(new ExpressionList<>(Arrays.asList(time, new StringValue(dmFormat))));
        return toDateFunction;
    }
}
