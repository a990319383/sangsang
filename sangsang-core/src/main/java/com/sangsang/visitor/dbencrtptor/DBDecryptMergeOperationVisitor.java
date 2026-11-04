package com.sangsang.visitor.dbencrtptor;

import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.enums.EncryptorFunctionEnum;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.visitor.EncryptorDbVisitorUtil;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.merge.MergeDelete;
import net.sf.jsqlparser.statement.merge.MergeInsert;
import net.sf.jsqlparser.statement.merge.MergeOperationVisitor;
import net.sf.jsqlparser.statement.merge.MergeUpdate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MERGE语句 matched/not matched 分支的加解密处理
 *
 * @author liutangqi
 * @date 2026/11/4 16:40
 */
public class DBDecryptMergeOperationVisitor extends BaseFieldParseTable implements MergeOperationVisitor {

    /**
     * 目标表的字段解析结果，用于处理insert分支中没有表别名的字段
     */
    private BaseFieldParseTable targetFieldParseTable;

    /**
     * 获取当前层解析实例
     *
     * @author liutangqi
     * @date 2026/11/4 16:40
     * @Param [baseFieldParseTable, targetFieldParseTable]
     **/
    public static DBDecryptMergeOperationVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable,
                                                                     BaseFieldParseTable targetFieldParseTable) {
        return new DBDecryptMergeOperationVisitor(baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap(),
                targetFieldParseTable);
    }

    private DBDecryptMergeOperationVisitor(int layer,
                                           Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap,
                                           Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap,
                                           BaseFieldParseTable targetFieldParseTable) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
        this.targetFieldParseTable = targetFieldParseTable;
    }

    @Override
    public void visit(MergeDelete mergeDelete) {
        mergeDelete.setAndPredicate(EncryptorDbVisitorUtil.expressionDecryption(mergeDelete.getAndPredicate(), this));
    }

    @Override
    public void visit(MergeUpdate mergeUpdate) {
        EncryptorDbVisitorUtil.updateSets(mergeUpdate.getUpdateSets(), this);
        mergeUpdate.setAndPredicate(EncryptorDbVisitorUtil.expressionDecryption(mergeUpdate.getAndPredicate(), this));
        mergeUpdate.setWhereCondition(EncryptorDbVisitorUtil.expressionDecryption(mergeUpdate.getWhereCondition(), this));
        mergeUpdate.setDeleteWhereCondition(EncryptorDbVisitorUtil.expressionDecryption(mergeUpdate.getDeleteWhereCondition(), this));
    }

    @Override
    public void visit(MergeInsert mergeInsert) {
        List<Column> columns = mergeInsert.getColumns();
        ExpressionList<Expression> values = mergeInsert.getValues();
        if (CollectionUtils.isNotEmpty(columns) && CollectionUtils.isNotEmpty(values) && columns.size() == values.size()) {
            for (int i = 0; i < columns.size(); i++) {
                FieldEncryptor fieldEncryptor = JsqlparserUtil.needEncryptFieldEncryptor(columns.get(i), this.targetFieldParseTable);
                Expression value = values.get(i);
                DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(this, EncryptorFunctionEnum.UPSTREAM_COLUMN, fieldEncryptor);
                value.accept(expressionVisitor);
                values.set(i, Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(value));
            }
        }
        mergeInsert.setAndPredicate(EncryptorDbVisitorUtil.expressionDecryption(mergeInsert.getAndPredicate(), this));
        mergeInsert.setWhereCondition(EncryptorDbVisitorUtil.expressionDecryption(mergeInsert.getWhereCondition(), this));
    }
}
