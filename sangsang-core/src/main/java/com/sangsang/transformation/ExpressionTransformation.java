package com.sangsang.transformation;

import net.sf.jsqlparser.expression.Expression;

/**
 * Expression 基类转换器
 * 部分语法转换后类型不一致时，才考虑使用此基类进行转换
 * 例如：group_concat
 *
 * @author liutangqi
 * @date 2025/5/30 15:05
 */
public abstract class ExpressionTransformation implements TransformationInterface<Expression> {
}
