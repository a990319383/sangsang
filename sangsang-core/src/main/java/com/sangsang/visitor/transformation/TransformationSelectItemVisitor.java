package com.sangsang.visitor.transformation;

import com.sangsang.cache.transformation.TransformationInstanceCache;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.visitor.transformation.wrap.ExpressionWrapper;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectItemVisitor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author liutangqi
 * @date 2025/5/21 15:21
 */
public class TransformationSelectItemVisitor extends BaseFieldParseTable implements SelectItemVisitor {

    /**
     * 获取当前层的实例
     *
     * @author liutangqi
     * @date 2025/5/27 11:19
     * @Param [baseFieldParseTable]
     **/
    public static TransformationSelectItemVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new TransformationSelectItemVisitor(baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap());
    }

    private TransformationSelectItemVisitor(int layer, Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    @Override
    public void visit(SelectItem selectItem) {
        Expression expression = selectItem.getExpression();
        TransformationExpressionVisitor tfExpressionVisitor = TransformationExpressionVisitor.newInstanceCurLayer(this);
        //使用包装类进行转转，额外对整个Expression进行语法转换一次
        Expression tfExp = ExpressionWrapper.wrap(expression).accept(tfExpressionVisitor);
        //表达式发生了替换，则替换此表达式
        Optional.ofNullable(tfExp).ifPresent(p -> selectItem.setExpression(p));

        //处理别名转换
        if (selectItem.getAlias() != null) {
            Alias tfAlias = TransformationInstanceCache.transformation(selectItem.getAlias());
            if (tfAlias != null) {
                //设置转换后的别名
                selectItem.setAlias(tfAlias);
            }
        }
    }
}
