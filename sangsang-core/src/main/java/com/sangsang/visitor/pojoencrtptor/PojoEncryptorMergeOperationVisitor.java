package com.sangsang.visitor.pojoencrtptor;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.util.visitor.EncryptorPojoVisitorUtil;
import net.sf.jsqlparser.statement.merge.MergeDelete;
import net.sf.jsqlparser.statement.merge.MergeInsert;
import net.sf.jsqlparser.statement.merge.MergeOperationVisitor;
import net.sf.jsqlparser.statement.merge.MergeUpdate;

import java.util.List;
import java.util.Map;

/**
 * MERGE语句 matched/not matched 分支的pojo模式占位符处理
 *
 * @author liutangqi
 * @date 2026/11/4 20:10
 */
public class PojoEncryptorMergeOperationVisitor extends BaseFieldParseTable implements MergeOperationVisitor {

    /**
     * 目标表的字段解析结果，用于处理insert分支中没有表别名的字段
     */
    private BaseFieldParseTable targetFieldParseTable;

    /**
     * 当前语句的占位符和字段对应关系
     */
    private Map<String, ColumnTableDto> placeholderColumnTableMap;

    /**
     * 获取当前层解析实例
     *
     * @author liutangqi
     * @date 2026/11/4 20:10
     * @Param [baseFieldParseTable, targetFieldParseTable, placeholderColumnTableMap]
     **/
    public static PojoEncryptorMergeOperationVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable,
                                                                          BaseFieldParseTable targetFieldParseTable,
                                                                          Map<String, ColumnTableDto> placeholderColumnTableMap) {
        return new PojoEncryptorMergeOperationVisitor(baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap(),
                targetFieldParseTable,
                placeholderColumnTableMap);
    }

    private PojoEncryptorMergeOperationVisitor(int layer,
                                                Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap,
                                                Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap,
                                                BaseFieldParseTable targetFieldParseTable,
                                                Map<String, ColumnTableDto> placeholderColumnTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
        this.targetFieldParseTable = targetFieldParseTable;
        this.placeholderColumnTableMap = placeholderColumnTableMap;
    }

    @Override
    public void visit(MergeDelete mergeDelete) {
        EncryptorPojoVisitorUtil.expression(mergeDelete.getAndPredicate(), this, this.placeholderColumnTableMap);
    }

    @Override
    public void visit(MergeUpdate mergeUpdate) {
        EncryptorPojoVisitorUtil.updateSets(mergeUpdate.getUpdateSets(), this, this.placeholderColumnTableMap);
        EncryptorPojoVisitorUtil.expression(mergeUpdate.getAndPredicate(), this, this.placeholderColumnTableMap);
        EncryptorPojoVisitorUtil.expression(mergeUpdate.getWhereCondition(), this, this.placeholderColumnTableMap);
        EncryptorPojoVisitorUtil.expression(mergeUpdate.getDeleteWhereCondition(), this, this.placeholderColumnTableMap);
    }

    @Override
    public void visit(MergeInsert mergeInsert) {
        EncryptorPojoVisitorUtil.columnsAndValues(mergeInsert.getColumns(), mergeInsert.getValues(), this.targetFieldParseTable, this.placeholderColumnTableMap);
        EncryptorPojoVisitorUtil.expression(mergeInsert.getAndPredicate(), this, this.placeholderColumnTableMap);
        EncryptorPojoVisitorUtil.expression(mergeInsert.getWhereCondition(), this, this.placeholderColumnTableMap);
    }
}
