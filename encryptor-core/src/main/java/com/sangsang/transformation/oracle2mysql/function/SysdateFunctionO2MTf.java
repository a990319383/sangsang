package com.sangsang.transformation.oracle2mysql.function;

import com.sangsang.transformation.FunctionTransformation;
import net.sf.jsqlparser.expression.Function;

/**
 * SYSDATE 转 NOW。
 *
 * @author liutangqi && codex && gpt-5.4
 * @date 2026/3/24 18:30
 */
public class SysdateFunctionO2MTf extends FunctionTransformation {
    @Override
    public boolean needTransformation(Function function) {
        return "SYSDATE".equalsIgnoreCase(function.getName());
    }

    @Override
    public Function doTransformation(Function function) {
        function.setName("NOW");
        return function;
    }
}
