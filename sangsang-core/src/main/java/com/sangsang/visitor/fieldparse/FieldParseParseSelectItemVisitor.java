package com.sangsang.visitor.fieldparse;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SelectItemVisitor;

import java.util.List;
import java.util.Map;

/**
 * 维护当前层所有的查询字段
 *
 * @author liutangqi
 * @date 2024/3/4 11:20
 */
public class FieldParseParseSelectItemVisitor extends BaseFieldParseTable implements SelectItemVisitor {


    /**
     * 获取当前层实例对象
     *
     * @author liutangqi
     * @date 2025/3/4 17:25
     * @Param [baseFieldParseTable]
     **/
    public static FieldParseParseSelectItemVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new FieldParseParseSelectItemVisitor(
                baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap()
        );
    }

    private FieldParseParseSelectItemVisitor(int layer, Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    /**
     * select 具体的字段
     *
     * @author liutangqi
     * @date 2024/3/5 11:01
     * @Param [selectExpressionItem]
     **/
    @Override
    public void visit(SelectItem selectItem) {
        Expression expression = selectItem.getExpression();

        Alias alias = selectItem.getAlias();
        FieldParseParseExpressionVisitor fieldParseExpressionVisitor = FieldParseParseExpressionVisitor.newInstanceCurLayer(this, alias);
        expression.accept(fieldParseExpressionVisitor);

    }

}
