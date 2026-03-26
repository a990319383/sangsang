package com.sangsang.transformation.oracle2mysql.function;

import com.sangsang.transformation.FunctionTransformation;
import net.sf.jsqlparser.expression.Function;

/**
 * Oracle LENGTH 按字符长度计算，MySQL 对应使用 CHAR_LENGTH。
 *
 * @author liutangqi && codex && gpt-5.4
 * @date 2026/3/24 18:30
 */
public class LengthFunctionO2MTf extends FunctionTransformation {
    @Override
    public boolean needTransformation(Function function) {
        return "LENGTH".equalsIgnoreCase(function.getName());
    }

    @Override
    public Function doTransformation(Function function) {
        function.setName("CHAR_LENGTH");
        return function;
    }
}
