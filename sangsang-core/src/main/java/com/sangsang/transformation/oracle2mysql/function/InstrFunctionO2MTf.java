package com.sangsang.transformation.oracle2mysql.function;

import com.sangsang.transformation.FunctionTransformation;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;

import java.util.Arrays;

/**
 * INSTR(str, substr) 转 LOCATE(substr, str)。
 *
 * <p>这里只处理常见的两个参数写法，更多参数的 Oracle INSTR 语义
 * 与 MySQL LOCATE/POSITION 并不完全一致，先保持原样。</p>
 *
 * @author liutangqi && codex && gpt-5.4
 * @date 2026/3/24 18:30
 */
public class InstrFunctionO2MTf extends FunctionTransformation {
    @Override
    public boolean needTransformation(Function function) {
        return "INSTR".equalsIgnoreCase(function.getName());
    }

    @Override
    public Function doTransformation(Function function) {
        ExpressionList<Expression> parameters = (ExpressionList<Expression>) function.getParameters();
        if (parameters == null || parameters.size() != 2) {
            return function;
        }

        Expression str = parameters.get(0);
        Expression subStr = parameters.get(1);

        function.setName("LOCATE");
        function.setParameters(new ExpressionList<>(Arrays.asList(subStr, str)));
        return function;
    }
}
