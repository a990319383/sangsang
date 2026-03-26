package com.sangsang.transformation.mysql2dm.expression;

import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.transformation.ExpressionTransformation;
import com.sangsang.util.CollectionUtils;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;

import java.util.Optional;


/**
 * group_concat函数转换成LISTAGG
 *
 * @author liutangqi
 * @date 2025/5/30 14:22
 */
public class GroupConcatExpressionM2DTf extends ExpressionTransformation {
    @Override
    public boolean needTransformation(Expression groupConcatExpression) {
        //所有的group_concat都需要进行转换
        return groupConcatExpression instanceof MySQLGroupConcat;
    }

    @Override
    public Expression doTransformation(Expression groupConcatExpression) {
        MySQLGroupConcat groupConcat = (MySQLGroupConcat) groupConcatExpression;
        //0.表达式错误直接返回
        if (groupConcat.getExpressionList().size() < 1) {
            return groupConcatExpression;
        }

        //1.分隔符，没有显示指定，默认是逗号
        StringValue separator = Optional.ofNullable(groupConcat.getSeparator()).map(StringValue::new).orElse(new StringValue(SymbolConstant.COMMA));

        //2.group_concat的表字段(LISTAGG函数入参的表字段只能有1个，如果mysql的group_concat的表字段存在多个的话，需要包一层concat())
        Expression listaggColumn = null;
        ExpressionList<?> expressionList = groupConcat.getExpressionList();
        //2.1 只有一个字段，只需要把这个字段作为入参即可
        if (expressionList.size() == 1) {
            listaggColumn = expressionList.get(0);
        }
        //2.2 大于一个字段，需要使用concat拼接字段
        if (expressionList.size() > 1) {
            Function concatFunction = new Function();
            concatFunction.setName("concat");
            concatFunction.setParameters(expressionList);
            listaggColumn = concatFunction;
        }

        //3.没有order by 结果拼接成LISTAGG 返回即可
        if (CollectionUtils.isEmpty(groupConcat.getOrderByElements())) {
            Function listaggFunction = new Function();
            listaggFunction.setName("LISTAGG");
            ExpressionList<Expression> listaggParameters = new ExpressionList();
            listaggParameters.add(listaggColumn);
            listaggParameters.add(separator);
            listaggFunction.setParameters(listaggParameters);
            return listaggFunction;
        }

        //4. 存在order by 的字段，需要使用 WITHIN GROUP (ORDER BY id DESC)
        AnalyticExpression analyticExp = new AnalyticExpression();
        analyticExp.setName("LISTAGG");
        analyticExp.setExpression(listaggColumn);
        analyticExp.setOffset(separator);
        analyticExp.setType(AnalyticType.WITHIN_GROUP);
        WindowDefinition windowDefinition = new WindowDefinition();
        windowDefinition.setOrderByElements(groupConcat.getOrderByElements());
        analyticExp.setWindowDefinition(windowDefinition);
        return analyticExp;
    }
}
