package com.sangsang.transformation.oracle2mysql.function;

import com.sangsang.transformation.FunctionTransformation;
import net.sf.jsqlparser.expression.Function;

/**
 * SUBSTR 转 SUBSTRING。
 *
 * @author liutangqi && codex && gpt-5.4
 * @date 2026/3/24 18:30
 */
public class SubstrFunctionO2MTf extends FunctionTransformation {
    @Override
    public boolean needTransformation(Function function) {
        return "SUBSTR".equalsIgnoreCase(function.getName());
    }

    @Override
    public Function doTransformation(Function function) {
        function.setName("SUBSTRING");
        return function;
    }
}
