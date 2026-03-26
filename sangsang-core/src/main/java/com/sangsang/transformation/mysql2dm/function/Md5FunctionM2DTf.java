package com.sangsang.transformation.mysql2dm.function;

import com.sangsang.transformation.FunctionTransformation;
import com.sangsang.util.StringUtils;
import net.sf.jsqlparser.expression.Function;

/**
 * md5() 函数转换为 LOWER(MD5())
 * 栗子：MD5(123456) ===> LOWER(MD5(123456))
 *
 * @author liutangqi
 * @date 2025/5/30 10:39
 */
public class Md5FunctionM2DTf extends FunctionTransformation {
    @Override
    public boolean needTransformation(Function function) {
        return "md5".equalsIgnoreCase(function.getName());
    }

    @Override
    public Function doTransformation(Function function) {
        //构建lower函数
        Function lowerFunction = new Function();
        lowerFunction.setName("LOWER");

        //将当前的md5()作为lower的参数
        lowerFunction.setParameters(function);
        return lowerFunction;
    }
}
