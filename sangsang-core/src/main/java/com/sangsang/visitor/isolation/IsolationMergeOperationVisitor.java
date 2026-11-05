package com.sangsang.visitor.isolation;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.util.visitor.IsolationVisitorUtil;
import net.sf.jsqlparser.statement.merge.MergeDelete;
import net.sf.jsqlparser.statement.merge.MergeInsert;
import net.sf.jsqlparser.statement.merge.MergeOperationVisitor;
import net.sf.jsqlparser.statement.merge.MergeUpdate;

/**
 * MERGE语句 matched/not matched 分支的数据隔离处理
 *
 * @author liutangqi
 * @date 2026/11/4 20:30
 */
public class IsolationMergeOperationVisitor implements MergeOperationVisitor {

    /**
     * MERGE语句目标表和来源表的字段解析结果，用于处理分支表达式中的子查询
     */
    private BaseFieldParseTable fieldParseTable;

    /**
     * 直接来源表的字段解析结果，来源是子查询时已在子查询内部隔离，此字段为空
     */
    private BaseFieldParseTable sourceFieldParseTable;

    /**
     * 目标表的字段解析结果，仅matched分支需要追加目标表隔离条件
     */
    private BaseFieldParseTable targetFieldParseTable;

    public IsolationMergeOperationVisitor(BaseFieldParseTable fieldParseTable,
                                          BaseFieldParseTable sourceFieldParseTable,
                                          BaseFieldParseTable targetFieldParseTable) {
        this.fieldParseTable = fieldParseTable;
        this.sourceFieldParseTable = sourceFieldParseTable;
        this.targetFieldParseTable = targetFieldParseTable;
    }

    @Override
    public void visit(MergeDelete mergeDelete) {
        //DELETE属于matched分支，来源表和目标表隔离条件均需要生效
        mergeDelete.setAndPredicate(IsolationVisitorUtil.mergePredicate(mergeDelete.getAndPredicate(), this.sourceFieldParseTable, this.targetFieldParseTable));
    }

    @Override
    public void visit(MergeUpdate mergeUpdate) {
        IsolationExpressionVisitor expressionVisitor = IsolationExpressionVisitor.newInstanceCurLayer(this.fieldParseTable);

        //1.处理SET值以及Oracle扩展条件中可能存在的子查询
        IsolationVisitorUtil.updateSets(mergeUpdate.getUpdateSets(), expressionVisitor);
        IsolationVisitorUtil.expressions(java.util.Arrays.asList(mergeUpdate.getWhereCondition(), mergeUpdate.getDeleteWhereCondition()), expressionVisitor);

        //2.UPDATE属于matched分支，来源表和目标表隔离条件均需要生效
        mergeUpdate.setAndPredicate(IsolationVisitorUtil.mergePredicate(mergeUpdate.getAndPredicate(), this.sourceFieldParseTable, this.targetFieldParseTable));
    }

    @Override
    public void visit(MergeInsert mergeInsert) {
        //1.处理VALUES及Oracle扩展条件中可能存在的子查询
        IsolationExpressionVisitor expressionVisitor = IsolationExpressionVisitor.newInstanceCurLayer(this.fieldParseTable);
        IsolationVisitorUtil.expressions(mergeInsert.getValues(), expressionVisitor);
        IsolationVisitorUtil.expressions(java.util.Collections.singletonList(mergeInsert.getWhereCondition()), expressionVisitor);

        //2.INSERT分支只限制来源数据，不追加目标表隔离条件
        mergeInsert.setAndPredicate(IsolationVisitorUtil.mergePredicate(mergeInsert.getAndPredicate(), this.sourceFieldParseTable, null));
    }
}
