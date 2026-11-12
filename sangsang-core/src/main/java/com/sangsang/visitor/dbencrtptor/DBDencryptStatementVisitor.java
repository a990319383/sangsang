package com.sangsang.visitor.dbencrtptor;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.enums.EncryptorFunctionEnum;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.ExpressionsUtil;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.visitor.EncryptorDbVisitorUtil;
import com.sangsang.util.visitor.FieldParseVisitorUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        //1.1 from 后的表
        Table table = delete.getTable();
        Optional.ofNullable(table).ifPresent(p -> p.accept(fieldParseTableFromItemVisitor));

        //1.2 using 后的表
        FieldParseVisitorUtil.usingList(delete.getUsingList(), fieldParseTableFromItemVisitor);

        //1.3 join的表
        FieldParseVisitorUtil.joins(delete.getJoins(), fieldParseTableFromItemVisitor);

        //2.处理join右表的子查询和on条件
        EncryptorDbVisitorUtil.joins(delete.getJoins(), fieldParseTableFromItemVisitor);

        //3.将where 条件进行解密
        Expression where = delete.getWhere();
        if (where != null) {
            DBDecryptExpressionVisitor sDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor, EncryptorFunctionEnum.DEFAULT_DECRYPTION, null);
            where.accept(sDecryptExpressionVisitor);
            delete.setWhere(Optional.ofNullable(sDecryptExpressionVisitor.getProcessedExpression()).orElse(where));
        }

        //4.结果赋值
        this.resultSql = delete.toString();
    }

    @Override
    public void visit(Update update) {
        //1.解析涉及到的表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();

        //1.1 update的表
        Table table = update.getTable();
        table.accept(fieldParseTableFromItemVisitor);

        //1.2 join的表（不同数据库的join解析有些许差异，有的在startJoins中，有的在joins中）
        FieldParseVisitorUtil.joins(update.getStartJoins(), fieldParseTableFromItemVisitor);
        FieldParseVisitorUtil.joins(update.getJoins(), fieldParseTableFromItemVisitor);

        //1.3 from后的表
        FromItem fromItem = update.getFromItem();
        if (fromItem != null) {
            fromItem.accept(fieldParseTableFromItemVisitor);
        }

        //2.处理join右表的子查询和on条件
        EncryptorDbVisitorUtil.joins(update.getStartJoins(), fieldParseTableFromItemVisitor);
        EncryptorDbVisitorUtil.joins(update.getJoins(), fieldParseTableFromItemVisitor);

        //3.处理from后的子查询
        if (fromItem != null) {
            fromItem.accept(DBDecryptFromItemVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor));
        }

        //4.解密where 条件的数据
        Expression where = update.getWhere();
        if (where != null) {
            DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor, EncryptorFunctionEnum.DEFAULT_DECRYPTION, null);
            where.accept(expressionVisitor);
            //修改后的where赋值
            update.setWhere(Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(where));
        }

        //5.加密处理set的数据
        EncryptorDbVisitorUtil.updateSets(update.getUpdateSets(), fieldParseTableFromItemVisitor);

        //6.处理结果赋值
        this.resultSql = update.toString();

    }

    @Override
    public void visit(Insert insert) {
        //1. insert 的表
        Table table = insert.getTable();
        Map<String, FieldEncryptor> fieldEncryptMap = TableCache.getTableFieldEncryptInfo().getOrDefault(table.getName(), new FieldHashMapWrapper<>());

        //2.通过获取insert的字段信息，解析出每个字段所对应的@FieldEncryptor，方便对插入字段需要加密的进行处理
        List<FieldEncryptor> needEncryptFieldEncryptor = new ArrayList<>();
        // insert into 表  values( ?,?,?,?,?,?,?,?) 这种语法时，这里的 columns 就是空的 ，如果当前配置开启了自动读取表结构的话，我们是能获取到表字段顺序的，这里可以自动填充，否则拿不到表字段顺序，无法兼容此语法
        List<Column> columns = insert.getColumns();
        //2.1 遇到了这种语法，且未开启自动读取表结构，给用户警告日志，让用户自行调整
        if (CollectionUtils.isEmpty(columns) && !TableCache.getCurConfig().isAutoFill()) {
            //单纯给提示，方便使用方快速定位问题
            log.warn("【sangsang】insert 语句未指定表字段顺序，且当前未开启自动读取表结构，无法获取表字段顺序。此情况可能导致加解密处理错误！！！请规范语法或开启 sangsang.autoFill=true  原sql:{}", insert.toString());
        }
        //2.2 当前配置了自动读取表结构，这种情况下才能拿到真实的字段顺序，此时把字段进行补齐
        if (CollectionUtils.isEmpty(columns) && TableCache.getCurConfig().isAutoFill()) {
            List<String> fieldLinkedList = TableCache.getTableFieldMap().get(table.getName());
            columns = fieldLinkedList.stream().map(m -> ExpressionsUtil.buildColumn(m, table.getName())).collect(Collectors.toList());
            insert.setColumns(new ExpressionList(columns));
        }

        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            FieldEncryptor leftFieldEncryptor = fieldEncryptMap.get(column.getColumnName());
            needEncryptFieldEncryptor.add(i, leftFieldEncryptor);
        }

        //3.解析sql
        Select select = insert.getSelect();
        FieldParseParseTableSelectVisitor fPSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
        select.accept(fPSelectVisitor);

        //4.进行加解密处理
        DBDecryptSelectVisitor dbDecryptSelectVisitor = DBDecryptSelectVisitor.newInstanceCurLayer(fPSelectVisitor, needEncryptFieldEncryptor);
        select.accept(dbDecryptSelectVisitor);

        //5.处理冲突时需要更新的数据
        //5.1 解析当前insert的表结构信息
        FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        table.accept(fieldParseTableFromItemVisitor);
        //5.2 处理 mysql 的 DuplicateUpdateSets
        EncryptorDbVisitorUtil.updateSets(insert.getDuplicateUpdateSets(), fieldParseTableFromItemVisitor);
        //5.3 处理 PostgreSQL ON CONFLICT DO UPDATE
        EncryptorDbVisitorUtil.updateSets(Optional.ofNullable(insert.getConflictAction()).map(InsertConflictAction::getUpdateSets).orElse(CollectionUtils.EMPTY_LIST), fieldParseTableFromItemVisitor);

        //6.处理好的sql赋值
        this.resultSql = insert.toString();
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
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
        select.accept(fieldParseTableSelectVisitor);

        //2.将需要加密的字段进行加密处理
        DBDecryptSelectVisitor sDecryptSelectVisitor = DBDecryptSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor);
        select.accept(sDecryptSelectVisitor);

        //3.处理后的结果赋值
        this.resultSql = select.toString();
    }

    @Override
    public void visit(Upsert upsert) {
        //1.解析目标表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor fieldParseTable = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        upsert.getTable().accept(fieldParseTable);

        //2.处理REPLACE SET语法
        EncryptorDbVisitorUtil.updateSets(upsert.getUpdateSets(), fieldParseTable);
        EncryptorDbVisitorUtil.updateSets(upsert.getDuplicateUpdateSets(), fieldParseTable);

        //3.处理UPSERT VALUES/SELECT语法
        List<Column> columns = upsert.getColumns();
        Select select = upsert.getSelect();
        if (CollectionUtils.isNotEmpty(columns) && select != null) {
            List<FieldEncryptor> fieldEncryptors = columns.stream()
                    .map(m -> JsqlparserUtil.needEncryptFieldEncryptor(m, fieldParseTable))
                    .collect(Collectors.toList());
            FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
            select.accept(fieldParseTableSelectVisitor);
            select.accept(DBDecryptSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor, fieldEncryptors));
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
