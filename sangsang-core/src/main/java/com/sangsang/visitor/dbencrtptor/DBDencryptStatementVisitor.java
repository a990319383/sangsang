package com.sangsang.visitor.dbencrtptor;

import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.enums.EncryptorFunctionEnum;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.visitor.EncryptorDbVisitorUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import com.sangsang.visitor.fieldparse.FieldParseStatementVisitor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
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
import net.sf.jsqlparser.statement.insert.InsertConflictAction;
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.merge.MergeOperation;
import net.sf.jsqlparser.statement.refresh.RefreshMaterializedViewStatement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.show.ShowIndexStatement;
import net.sf.jsqlparser.statement.show.ShowTablesStatement;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.upsert.Upsert;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 解析sql的入口 （crud所有类型的sql ）
 *
 * @author liutangqi
 * @date 2024/2/29 17:55
 */
@Slf4j
public class DBDencryptStatementVisitor implements StatementVisitor {
    /**
     * 加密完成后的sql
     */
    private String resultSql;

    public String getResultSql() {
        return resultSql;
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
        //1.解析涉及到的表拥有的全部字段信息
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        delete.accept(fpStatementVisitor);

        //2.处理join右表的子查询和on条件
        EncryptorDbVisitorUtil.joins(delete.getJoins(), fpStatementVisitor);

        //3.将where 条件进行解密
        Expression where = delete.getWhere();
        if (where != null) {
            DBDecryptExpressionVisitor sDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(fpStatementVisitor, EncryptorFunctionEnum.DEFAULT_DECRYPTION, null);
            where.accept(sDecryptExpressionVisitor);
            delete.setWhere(Optional.ofNullable(sDecryptExpressionVisitor.getProcessedExpression()).orElse(where));
        }

        //4.处理DML返回列
        EncryptorDbVisitorUtil.returning(delete.getReturningClause(), delete.getTable(), fpStatementVisitor);
        EncryptorDbVisitorUtil.output(delete.getOutputClause(), delete.getTable(), fpStatementVisitor);

        //5.结果赋值
        this.resultSql = JsqlparserUtil.perfectOutputIntoSql(delete.toString(), delete.getOutputClause());
    }

    @Override
    public void visit(Update update) {
        //1.解析涉及到的表拥有的全部字段信息
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        update.accept(fpStatementVisitor);

        //2.处理join右表的子查询和on条件
        EncryptorDbVisitorUtil.joins(update.getStartJoins(), fpStatementVisitor);
        EncryptorDbVisitorUtil.joins(update.getJoins(), fpStatementVisitor);

        //3.处理from后的子查询
        Optional.ofNullable(update.getFromItem())
                .ifPresent(p -> p.accept(DBDecryptFromItemVisitor.newInstanceCurLayer(fpStatementVisitor)));

        //4.解密where 条件的数据
        Expression where = update.getWhere();
        if (where != null) {
            DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(fpStatementVisitor, EncryptorFunctionEnum.DEFAULT_DECRYPTION, null);
            where.accept(expressionVisitor);
            //修改后的where赋值
            update.setWhere(Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(where));
        }

        //5.加密处理set的数据
        EncryptorDbVisitorUtil.updateSets(update.getUpdateSets(), fpStatementVisitor);

        //6.处理DML返回列
        EncryptorDbVisitorUtil.returning(update.getReturningClause(), update.getTable(), fpStatementVisitor);
        EncryptorDbVisitorUtil.output(update.getOutputClause(), update.getTable(), fpStatementVisitor);

        //7.处理结果赋值
        this.resultSql = JsqlparserUtil.perfectOutputIntoSql(update.toString(), update.getOutputClause());

    }

    @Override
    public void visit(Insert insert) {
        //1.特殊语法处理，如果insert into 后面的表没有跟具体字段的话，根据情况进行完善
        JsqlparserUtil.perfectColumn(insert);

        //2.解析sql
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        insert.accept(fpStatementVisitor);

        //3.获取每个Column字段对应的@FieldEncryptor 注解信息
        List<FieldEncryptor> needEncryptFieldEncryptor = JsqlparserUtil.parseInsertFieldEncryptor(insert.getTable(), insert.getColumns());

        //4.select进行加解密处理
        Optional.ofNullable(insert.getSelect())
                .ifPresent(p -> p.accept(DBDecryptSelectVisitor.newInstanceNextLayer(fpStatementVisitor, needEncryptFieldEncryptor)));


        //5.处理冲突时需要更新的数据
        //5.1 处理 mysql 的 DuplicateUpdateSets
        EncryptorDbVisitorUtil.updateSets(insert.getDuplicateUpdateSets(), fpStatementVisitor);
        //5.2 处理 PostgreSQL ON CONFLICT DO UPDATE
        EncryptorDbVisitorUtil.updateSets(Optional.ofNullable(insert.getConflictAction()).map(InsertConflictAction::getUpdateSets).orElse(CollectionUtils.EMPTY_LIST), fpStatementVisitor);
        //5.3 处理 INSERT SET
        EncryptorDbVisitorUtil.updateSets(insert.getSetUpdateSets(), fpStatementVisitor);

        //6.处理DML返回列
        EncryptorDbVisitorUtil.returning(insert.getReturningClause(), insert.getTable(), fpStatementVisitor);
        EncryptorDbVisitorUtil.output(insert.getOutputClause(), insert.getTable(), fpStatementVisitor);

        //7.处理好的sql赋值
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
    public void visit(CreateSchema createSchema) {

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
    public void visit(ResetStatement resetStatement) {

    }


    @Override
    public void visit(ShowColumnsStatement set) {

    }

    @Override
    public void visit(ShowIndexStatement showIndex) {

    }

    @Override
    public void visit(ShowTablesStatement showTablesStatement) {

    }


    @Override
    public void visit(Merge merge) {
        //1.处理CTE内部的查询
        EncryptorDbVisitorUtil.cte(merge.getWithItemsList());

        //2.解析目标表和using来源表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor fieldParseTable = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        merge.getTable().accept(fieldParseTable);
        FromItem fromItem = merge.getFromItem();
        if (fromItem != null) {
            fromItem.accept(fieldParseTable);
            //using来源是子查询时，对子查询进行加解密处理
            fromItem.accept(DBDecryptFromItemVisitor.newInstanceCurLayer(fieldParseTable));
        }

        //3.处理on条件
        Expression onCondition = merge.getOnCondition();
        if (onCondition != null) {
            DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(fieldParseTable, EncryptorFunctionEnum.DEFAULT_DECRYPTION, null);
            onCondition.accept(expressionVisitor);
            merge.setOnCondition(Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(onCondition));
        }

        //4.单独解析目标表，用于处理not matched insert分支中没有表别名的目标字段
        FieldParseParseTableFromItemVisitor targetFieldParseTable = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        merge.getTable().accept(targetFieldParseTable);

        //5.处理matched/not matched的全部分支
        DBDecryptMergeOperationVisitor mergeOperationVisitor = DBDecryptMergeOperationVisitor.newInstanceCurLayer(fieldParseTable, targetFieldParseTable);
        List<MergeOperation> operations = Optional.ofNullable(merge.getOperations()).orElse(CollectionUtils.EMPTY_LIST);
        for (MergeOperation operation : operations) {
            operation.accept(mergeOperationVisitor);
        }

        //6.处理结果赋值
        this.resultSql = merge.toString();

    }

    /**
     * 给select语句进行加密
     *
     * @author liutangqi
     * @date 2024/2/29 17:56
     * @Param [select]
     **/
    @Override
    public void visit(Select select) {
        //1.解析当前sql拥有的全部字段信息
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        select.accept(fpStatementVisitor);

        //2.将需要加密的字段进行加密处理
        DBDecryptSelectVisitor sDecryptSelectVisitor = DBDecryptSelectVisitor.newInstanceCurLayer(fpStatementVisitor);
        select.accept(sDecryptSelectVisitor);

        //3.处理后的结果赋值
        this.resultSql = select.toString();
    }

    @Override
    public void visit(Upsert upsert) {
        //1.解析目标表拥有的全部字段信息
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        upsert.accept(fpStatementVisitor);

        //2.处理REPLACE SET语法
        EncryptorDbVisitorUtil.updateSets(upsert.getUpdateSets(), fpStatementVisitor);
        EncryptorDbVisitorUtil.updateSets(upsert.getDuplicateUpdateSets(), fpStatementVisitor);

        //3.处理UPSERT VALUES/SELECT语法
        List<Column> columns = upsert.getColumns();
        Select select = upsert.getSelect();
        if (CollectionUtils.isNotEmpty(columns) && select != null) {
            List<FieldEncryptor> fieldEncryptors = columns.stream()
                    .map(m -> JsqlparserUtil.needEncryptFieldEncryptor(m, fpStatementVisitor))
                    .collect(Collectors.toList());
            select.accept(DBDecryptSelectVisitor.newInstanceCurLayer(fpStatementVisitor, fieldEncryptors));
        }

        //4.处理结果赋值
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
    public void visit(IfElseStatement ifElseStatement) {

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
