package com.sangsang.visitor.transformation;

import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.visitor.TransformationVisitorUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import com.sangsang.visitor.fieldparse.FieldParseStatementVisitor;
import com.sangsang.visitor.transformation.wrap.ExpressionWrapper;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.*;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterSession;
import net.sf.jsqlparser.statement.alter.AlterSystemStatement;
import net.sf.jsqlparser.statement.alter.RenameTableStatement;
import net.sf.jsqlparser.statement.alter.sequence.AlterSequence;
import net.sf.jsqlparser.statement.analyze.Analyze;
import net.sf.jsqlparser.statement.comment.Comment;
import net.sf.jsqlparser.statement.create.index.CreateIndex;
import net.sf.jsqlparser.statement.create.schema.CreateSchema;
import net.sf.jsqlparser.statement.create.sequence.CreateSequence;
import net.sf.jsqlparser.statement.create.synonym.CreateSynonym;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.view.AlterView;
import net.sf.jsqlparser.statement.create.view.CreateView;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.execute.Execute;
import net.sf.jsqlparser.statement.grant.Grant;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.merge.MergeOperation;
import net.sf.jsqlparser.statement.refresh.RefreshMaterializedViewStatement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.show.ShowIndexStatement;
import net.sf.jsqlparser.statement.show.ShowTablesStatement;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.upsert.Upsert;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author liutangqi
 * @date 2025/5/21 10:46
 */
public class TransformationStatementVisitor implements StatementVisitor {
    /**
     * 处理后的sql
     */
    private String resultSql;

    public String getResultSql() {
        return this.resultSql;
    }

    @Override
    public void visit(Analyze analyze) {

    }

    @Override
    public void visit(SavepointStatement savepointStatement) {

    }

    @Override
    public void visit(RollbackStatement rollbackStatement) {

    }

    @Override
    public void visit(Comment comment) {

    }

    @Override
    public void visit(Commit commit) {

    }

    @Override
    public void visit(Delete delete) {
        //1.解析表字段
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        delete.accept(fpStatementVisitor);

        //2.将表进行转换
        Table table = delete.getTable();
        List<Join> joins = Optional.ofNullable(delete.getJoins()).orElse(new ArrayList<>());
        TransformationFromItemVisitor tfFromItemVisitor = TransformationFromItemVisitor.newInstanceCurLayer(fpStatementVisitor);
        table.accept(tfFromItemVisitor);
        for (Join join : joins) {
            join.getRightItem().accept(tfFromItemVisitor);
        }

        //3.处理where条件
        TransformationExpressionVisitor tfExpressionVisitor = TransformationExpressionVisitor.newInstanceCurLayer(fpStatementVisitor);
        //使用包装类进行转转，额外对整个Expression进行语法转换一次
        Expression tfExp = ExpressionWrapper.wrap(delete.getWhere()).accept(tfExpressionVisitor);
        Optional.ofNullable(tfExp).ifPresent(p -> delete.setWhere(p));

        //4.结果赋值
        this.resultSql = JsqlparserUtil.perfectOutputIntoSql(delete.toString(), delete.getOutputClause());
    }

    @Override
    public void visit(Update update) {
        //1.解析涉及到的表拥有的全部字段信息
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        update.accept(fpStatementVisitor);

        //2.将update和join的表进行转换
        Table table = update.getTable();
        List<Join> joins = Optional.ofNullable(update.getStartJoins()).orElse(new ArrayList<>());
        TransformationFromItemVisitor tfFromItemVisitor = TransformationFromItemVisitor.newInstanceCurLayer(fpStatementVisitor);
        table.accept(tfFromItemVisitor);
        //join的表
        for (Join join : joins) {
            join.getRightItem().accept(tfFromItemVisitor);
        }

        //3.处理where条件
        Expression where = update.getWhere();
        if (where != null) {
            TransformationExpressionVisitor tfExpressionVisitor = TransformationExpressionVisitor.newInstanceCurLayer(fpStatementVisitor);
            //使用包装类进行转转，额外对整个Expression进行语法转换一次
            Expression tfExp = ExpressionWrapper.wrap(where).accept(tfExpressionVisitor);
            Optional.ofNullable(tfExp).ifPresent(p -> update.setWhere(p));
        }

        //4.处理set的值
        TransformationVisitorUtil.updateSets(update.getUpdateSets(), fpStatementVisitor);

        //5.处理结果赋值
        this.resultSql = JsqlparserUtil.perfectOutputIntoSql(update.toString(), update.getOutputClause());
    }

    @Override
    public void visit(Insert insert) {
        //1.解析select的表字段信息
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        insert.accept(fpStatementVisitor);

        //2.处理select
        if (insert.getSelect() != null) {
            //2.1 先解析select的表字段信息
            Select select = insert.getSelect();
            TransformationSelectVisitor tfSelectVisitor = TransformationSelectVisitor.newInstanceNextLayer(fpStatementVisitor);
            select.accept(tfSelectVisitor);
            //2.2 如果整个select发生了改写，则将改写后的进行赋值
            Optional.ofNullable(tfSelectVisitor.getProcessedPlainSelect())
                    .ifPresent(p -> insert.setSelect(p));
        }


        //3.处理Column
        TransformationVisitorUtil.columns(insert.getColumns());

        //4.处理table
        Table table = insert.getTable();
        table.accept(TransformationFromItemVisitor.newInstanceCurLayer(fpStatementVisitor));

        //5.结果赋值
        this.resultSql = JsqlparserUtil.perfectOutputIntoSql(insert.toString(), insert.getOutputClause());

    }

    @Override
    public void visit(Drop drop) {

    }

    @Override
    public void visit(Truncate truncate) {

    }

    @Override
    public void visit(CreateIndex createIndex) {

    }

    @Override
    public void visit(CreateSchema aThis) {

    }

    @Override
    public void visit(CreateTable createTable) {

    }

    @Override
    public void visit(CreateView createView) {

    }

    @Override
    public void visit(AlterView alterView) {

    }

    @Override
    public void visit(RefreshMaterializedViewStatement materializedView) {

    }

    @Override
    public void visit(Alter alter) {

    }

    @Override
    public void visit(Statements stmts) {

    }

    @Override
    public void visit(Execute execute) {

    }

    @Override
    public void visit(SetStatement set) {

    }

    @Override
    public void visit(ResetStatement reset) {

    }

    @Override
    public void visit(ShowColumnsStatement set) {

    }

    @Override
    public void visit(ShowIndexStatement showIndex) {

    }

    @Override
    public void visit(ShowTablesStatement showTables) {

    }

    @Override
    public void visit(Merge merge) {
        //1.解析目标表和using来源表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor fieldParseTable = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        merge.getTable().accept(fieldParseTable);
        FromItem fromItem = merge.getFromItem();
        if (fromItem != null) {
            fromItem.accept(fieldParseTable);
        }

        //2.处理CTE、目标表和using来源表
        TransformationVisitorUtil.cte(merge.getWithItemsList());
        TransformationFromItemVisitor fromItemVisitor = TransformationFromItemVisitor.newInstanceCurLayer(fieldParseTable);
        merge.getTable().accept(fromItemVisitor);
        if (fromItem != null) {
            fromItem.accept(fromItemVisitor);
        }

        //3.处理ON条件
        merge.setOnCondition(TransformationVisitorUtil.expression(merge.getOnCondition(), fieldParseTable));

        //4.处理matched/not matched的全部分支，保留原MERGE语句结构
        TransformationMergeOperationVisitor mergeOperationVisitor = TransformationMergeOperationVisitor.newInstanceCurLayer(fieldParseTable);
        List<MergeOperation> operations = Optional.ofNullable(merge.getOperations()).orElse(CollectionUtils.EMPTY_LIST);
        for (MergeOperation operation : operations) {
            operation.accept(mergeOperationVisitor);
        }

        //5.处理结果赋值
        this.resultSql = merge.toString();

    }

    @Override
    public void visit(Select select) {
        //1解析当前sql拥有的全部字段信息
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        select.accept(fpStatementVisitor);

        //2.进行语法转换
        TransformationSelectVisitor selectVisitor = TransformationSelectVisitor.newInstanceCurLayer(fpStatementVisitor);
        select.accept(selectVisitor);

        //3.处理好的sql赋值
        this.resultSql = Optional.ofNullable(selectVisitor.getProcessedPlainSelect()).map(Objects::toString).orElse(select.toString());
    }

    @Override
    public void visit(Upsert upsert) {
        //1.解析目标表拥有的全部字段信息
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        upsert.accept(fpStatementVisitor);

        //2.处理UPSERT VALUES/SELECT中的值及来源查询
        upsert.setSelect(TransformationVisitorUtil.select(upsert.getSelect()));

        //3.处理目标字段、REPLACE SET及冲突更新分支
        TransformationVisitorUtil.columns(upsert.getColumns());
        TransformationVisitorUtil.updateSets(upsert.getUpdateSets(), fpStatementVisitor);
        TransformationVisitorUtil.updateSets(upsert.getDuplicateUpdateSets(), fpStatementVisitor);

        //4.处理目标表，语句关键字和整体结构保持不变
        upsert.getTable().accept(TransformationFromItemVisitor.newInstanceCurLayer(fpStatementVisitor));

        //5.处理结果赋值
        this.resultSql = upsert.toString();

    }

    @Override
    public void visit(UseStatement use) {

    }

    @Override
    public void visit(Block block) {

    }

    @Override
    public void visit(DescribeStatement describe) {

    }

    @Override
    public void visit(ExplainStatement aThis) {

    }

    @Override
    public void visit(ShowStatement aThis) {

    }

    @Override
    public void visit(DeclareStatement aThis) {

    }

    @Override
    public void visit(Grant grant) {

    }

    @Override
    public void visit(CreateSequence createSequence) {

    }

    @Override
    public void visit(AlterSequence alterSequence) {

    }

    @Override
    public void visit(CreateFunctionalStatement createFunctionalStatement) {

    }

    @Override
    public void visit(CreateSynonym createSynonym) {

    }

    @Override
    public void visit(AlterSession alterSession) {

    }

    @Override
    public void visit(IfElseStatement aThis) {

    }

    @Override
    public void visit(RenameTableStatement renameTableStatement) {

    }

    @Override
    public void visit(PurgeStatement purgeStatement) {

    }

    @Override
    public void visit(AlterSystemStatement alterSystemStatement) {

    }

    @Override
    public void visit(UnsupportedStatement unsupportedStatement) {

    }
}
