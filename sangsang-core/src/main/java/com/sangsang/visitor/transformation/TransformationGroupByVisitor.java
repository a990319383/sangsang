package com.sangsang.visitor.transformation;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.visitor.transformation.wrap.ExpressionWrapper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.GroupByVisitor;

import java.util.*;

/**
 * @author liutangqi
 * @date 2025/10/30 16:06
 */
public class TransformationGroupByVisitor extends BaseFieldParseTable implements GroupByVisitor {

    /**
     * 获取当前层实例
     *
     * @author liutangqi
     * @date 2025/10/30 16:08
     * @Param [baseFieldParseTable]
     **/
    public static TransformationGroupByVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new TransformationGroupByVisitor(
                baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap()
        );
    }

    private TransformationGroupByVisitor(int layer, Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    @Override
    public void visit(GroupByElement groupBy) {
        //1.获取当前所有的group by 后面的表达式
        ExpressionList<Expression> groupByExpressionList = groupBy.getGroupByExpressionList();

        //2.创建表达式解析器，挨个处理每个表达式
        TransformationExpressionVisitor tfExpressionVisitor = TransformationExpressionVisitor.newInstanceCurLayer(this);
        List<Expression> resExpreessions = new ArrayList<>();
        for (Expression expression : groupByExpressionList) {
            //使用包装类进行转转，额外对整个Expression进行语法转换一次
            Expression tfExp = ExpressionWrapper.wrap(expression).accept(tfExpressionVisitor);
            resExpreessions.add(Optional.ofNullable(tfExp).orElse(expression));
        }

        //3.处理后的表达式重新赋值
        groupBy.setGroupByExpressions(new ExpressionList(resExpreessions));
    }
}
