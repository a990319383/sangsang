package com.sangsang.transformation.mysql2dm.function;

import com.sangsang.transformation.FunctionTransformation;
import com.sangsang.util.StringUtils;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.schema.Column;

/**
 * DATEDIFF 函数适配
 * DATEDIFF(t1,t2) ==> DATEDIFF(DAY,t2,t1)
 *
 * @author liutangqi
 * @date 2025/6/23 10:31
 */
public class DateDiffFunctionM2DTf extends FunctionTransformation {
    private static final Column DAY_COLUMN = new Column("DAY");

    @Override
    public boolean needTransformation(Function function) {
        return "DATEDIFF".equalsIgnoreCase(function.getName());
    }

    @Override
    public Function doTransformation(Function function) {
        //1.参数不是2个，可能是已经改成了达梦的语法，直接返回旧的
        if (function.getParameters().size() != 2) {
            return function;
        }

        //2.提取参数
        Expression t1 = function.getParameters().get(0);
        Expression t2 = function.getParameters().get(1);

        //3.拼接新参数
        function.setParameters(DAY_COLUMN, t2, t1);
        return function;
    }
}
