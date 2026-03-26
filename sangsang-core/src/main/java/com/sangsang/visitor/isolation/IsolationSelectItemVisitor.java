package com.sangsang.visitor.isolation;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectItemVisitor;

import java.util.List;
import java.util.Map;

/**
 * @author liutangqi
 * @date 2025/6/13 16:55
 */
public class IsolationSelectItemVisitor extends BaseFieldParseTable implements SelectItemVisitor {
    /**
     * 获取当前层实例
     *
     * @author liutangqi
     * @date 2025/6/13 16:55
     * @Param [baseFieldParseTable]
     **/
    public static IsolationSelectItemVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new IsolationSelectItemVisitor(baseFieldParseTable.getLayer(), baseFieldParseTable.getLayerSelectTableFieldMap(), baseFieldParseTable.getLayerFieldTableMap());
    }

    private IsolationSelectItemVisitor(int layer, Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    @Override
    public void visit(SelectItem selectItem) {
        Expression expression = selectItem.getExpression();
        expression.accept(IsolationExpressionVisitor.newInstanceCurLayer(this));
    }
}
