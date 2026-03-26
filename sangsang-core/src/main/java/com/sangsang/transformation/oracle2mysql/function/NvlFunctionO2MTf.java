package com.sangsang.transformation.oracle2mysql.function;

import com.sangsang.transformation.FunctionTransformation;
import net.sf.jsqlparser.expression.Function;

/**
 * NVL 转 IFNULL。
 *
 * @author liutangqi && codex && gpt-5.4
 * @date 2026/3/24 18:30
 */
public class NvlFunctionO2MTf extends FunctionTransformation {
    @Override
    public boolean needTransformation(Function function) {
        return "NVL".equalsIgnoreCase(function.getName());
    }

    @Override
    public Function doTransformation(Function function) {
        function.setName("IFNULL");
        return function;
    }
}
