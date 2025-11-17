package com.sangsang.transformation.mysql2dm.function;

import com.sangsang.transformation.FunctionTransformation;
import com.sangsang.util.StringUtils;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;

/**
 * date(now())函数转换为 DATE_FORMAT(now(), '%Y-%m-%d')
 * 栗子：date(now()) ==>  DATE_FORMAT(now(), '%Y-%m-%d')
 *
 * @author liutangqi
 * @date 2025/5/22 14:33
 * @Param
 **/
public class DateFunctionM2DTf extends FunctionTransformation {
    @Override
    public boolean needTransformation(Function function) {
        return "DATE".equalsIgnoreCase(function.getName());
    }

    @Override
    public Function doTransformation(Function function) {
        ExpressionList<?> parameters = function.getParameters();
        //1.参数有误，直接返回
        if (parameters.size() != 1) {
            return function;
        }

        //2.获取核心的几个参数 栗子：date(now()) ==>  DATE_FORMAT(now(), '%Y-%m-%d')
        //时间（栗子中的NOW()）
        Expression timeExp = parameters.get(0);

        //3.拼凑新的表达式
        Function dateAddFunction = new Function();
        dateAddFunction.setName("DATE_FORMAT");
        ExpressionList<Expression> dateAddParameters = new ExpressionList();
        dateAddParameters.add(timeExp);
        dateAddParameters.add(new StringValue("%Y-%m-%d"));
        dateAddFunction.setParameters(dateAddParameters);
        return dateAddFunction;
    }
}