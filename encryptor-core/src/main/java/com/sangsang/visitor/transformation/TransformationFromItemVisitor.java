package com.sangsang.visitor.transformation;

import com.sangsang.cache.transformation.TransformationInstanceCache;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author liutangqi
 * @date 2025/5/28 14:02
 */
public class TransformationFromItemVisitor extends BaseFieldParseTable implements FromItemVisitor {

    /**
     * 获取当前层实例
     *
     * @author liutangqi
     * @date 2025/5/28 14:02
     * @Param [baseFieldParseTable]
     **/
    public static TransformationFromItemVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new TransformationFromItemVisitor(baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap());
    }

    private TransformationFromItemVisitor(int layer, Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    @Override
    public void visit(Table table) {
        Table tfTable = TransformationInstanceCache.transformation(table);
        if (tfTable != null) {
            //记录处理完成后的表达式
            table = tfTable;
        }
    }

    /**
     * 嵌套子查询 再解析里层的内容
     *
     * @author liutangqi
     * @date 2025/5/28 14:04
     * @Param [selectBody]
     **/
    @Override
    public void visit(ParenthesedSelect selectBody) {
        //转换子查询内容（注意：这里是下一层的）
        if (selectBody.getSelect() != null) {
            TransformationSelectVisitor tfSelectVisitor = TransformationSelectVisitor.newInstanceNextLayer(this);
            selectBody.getSelect().accept(tfSelectVisitor);
            Optional.ofNullable(tfSelectVisitor.getProcessedPlainSelect()).ifPresent(p -> selectBody.setSelect(p));
        }
    }

    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }

    @Override
    public void visit(TableFunction tableFunction) {

    }

    @Override
    public void visit(ParenthesedFromItem aThis) {

    }
}
