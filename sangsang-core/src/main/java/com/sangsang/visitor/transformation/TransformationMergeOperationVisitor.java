package com.sangsang.visitor.transformation;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.util.visitor.TransformationVisitorUtil;
import net.sf.jsqlparser.statement.merge.MergeDelete;
import net.sf.jsqlparser.statement.merge.MergeInsert;
import net.sf.jsqlparser.statement.merge.MergeOperationVisitor;
import net.sf.jsqlparser.statement.merge.MergeUpdate;

import java.util.List;
import java.util.Map;

/**
 * MERGE语句 matched/not matched 分支的语法转换处理
 *
 * @author liutangqi
 * @date 2026/11/4 20:40
 */
public class TransformationMergeOperationVisitor extends BaseFieldParseTable implements MergeOperationVisitor {

    /**
     * 获取当前层解析实例
     *
     * @author liutangqi
     * @date 2026/11/4 20:40
     * @Param [baseFieldParseTable]
     **/
    public static TransformationMergeOperationVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new TransformationMergeOperationVisitor(baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap());
    }

    private TransformationMergeOperationVisitor(int layer,
                                                 Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap,
                                                 Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    @Override
    public void visit(MergeDelete mergeDelete) {
        mergeDelete.setAndPredicate(TransformationVisitorUtil.expression(mergeDelete.getAndPredicate(), this));
    }

    @Override
    public void visit(MergeUpdate mergeUpdate) {
        TransformationVisitorUtil.updateSets(mergeUpdate.getUpdateSets(), this);
        mergeUpdate.setAndPredicate(TransformationVisitorUtil.expression(mergeUpdate.getAndPredicate(), this));
        mergeUpdate.setWhereCondition(TransformationVisitorUtil.expression(mergeUpdate.getWhereCondition(), this));
        mergeUpdate.setDeleteWhereCondition(TransformationVisitorUtil.expression(mergeUpdate.getDeleteWhereCondition(), this));
    }

    @Override
    public void visit(MergeInsert mergeInsert) {
        TransformationVisitorUtil.columnsAndValues(mergeInsert.getColumns(), mergeInsert.getValues(), this);
        mergeInsert.setAndPredicate(TransformationVisitorUtil.expression(mergeInsert.getAndPredicate(), this));
        mergeInsert.setWhereCondition(TransformationVisitorUtil.expression(mergeInsert.getWhereCondition(), this));
    }
}
