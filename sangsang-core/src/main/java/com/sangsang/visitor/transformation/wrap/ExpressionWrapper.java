package com.sangsang.visitor.transformation.wrap;

import com.sangsang.cache.transformation.TransformationInstanceCache;
import com.sangsang.visitor.transformation.TransformationExpressionVisitor;
import net.sf.jsqlparser.expression.Expression;

import java.util.Optional;

/**
 * 将Expression 进行包装，在进行细的语法解析后，将Expression 再进行一次整体的语法解析
 *
 * @author liutangqi
 * @date 2025/6/6 9:29
 */
public class ExpressionWrapper<T extends Expression> {

    /**
     * 原始的需要包装的表达式
     */
    private T orgExp;

    private ExpressionWrapper(T orgExp) {
        this.orgExp = orgExp;
    }

    /**
     * 获取包装对象
     *
     * @author liutangqi
     * @date 2025/6/6 9:44
     * @Param [orgExp]
     **/
    public static <T extends Expression> ExpressionWrapper wrap(T orgExp) {
        return new ExpressionWrapper(orgExp);
    }

    /**
     * 进行访问者的处理
     * 并将整个Expression尝试进行语法转换
     *
     * @author liutangqi
     * @date 2025/6/6 9:44
     * @Param [expressionVisitor]
     **/
    public Expression accept(TransformationExpressionVisitor tfExpressionVisitor) {
        //1.先走处理逻辑，将这个表达式拆解后进行语法转换
        orgExp.accept(tfExpressionVisitor);

        //2.提取语法拆解后的表达式
        Expression tfExp = Optional.ofNullable(tfExpressionVisitor.getExpression()).orElse(orgExp);

        //3.将这个语法表达式整体进行一个语法转换
        return Optional.ofNullable(TransformationInstanceCache.transformation(tfExp, Expression.class))
                .orElse(tfExp);
    }
}
