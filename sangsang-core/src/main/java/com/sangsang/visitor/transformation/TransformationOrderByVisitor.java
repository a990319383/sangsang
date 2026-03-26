package com.sangsang.visitor.transformation;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.visitor.transformation.wrap.ExpressionWrapper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.OrderByVisitor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author liutangqi
 * @date 2025/5/30 14:56
 */
public class TransformationOrderByVisitor extends BaseFieldParseTable implements OrderByVisitor {

    /**
     * 获取当前层实例
     *
     * @author liutangqi
     * @date 2025/5/30 14:57
     * @Param [baseField]
     **/
    public static TransformationOrderByVisitor newInstanceCurLayer(BaseFieldParseTable baseField) {
        return new TransformationOrderByVisitor(
                baseField.getLayer(),
                baseField.getLayerSelectTableFieldMap(),
                baseField.getLayerFieldTableMap()
        );
    }

    private TransformationOrderByVisitor(int layer, Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    @Override
    public void visit(OrderByElement orderBy) {
        Expression expression = orderBy.getExpression();
        if (expression != null) {
            TransformationExpressionVisitor tfExpressionVisitor = TransformationExpressionVisitor.newInstanceCurLayer(this);
            //使用包装类进行转转，额外对整个Expression进行语法转换一次
            Expression tfExp = ExpressionWrapper.wrap(expression).accept(tfExpressionVisitor);
            Optional.ofNullable(tfExp).ifPresent(p -> orderBy.setExpression(p));
        }

        //order by 后显示指定null值是排在最开始还是排在最后， 栗子： SELECT * FROM orders ORDER BY amount DESC NULLS LAST;  -- 明确要求 NULL 值排在最后
        //这里的场景不需要处理这个语法
//        OrderByElement.NullOrdering nullOrdering = orderBy.getNullOrdering();

    }
}
