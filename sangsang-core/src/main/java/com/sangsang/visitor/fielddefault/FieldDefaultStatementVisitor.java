package com.sangsang.visitor.fielddefault;

import cn.hutool.core.collection.CollectionUtil;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.annos.fielddefault.FieldDefault;
import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.enums.SqlCommandEnum;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.visitor.FieldDefaultVisitorUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import com.sangsang.visitor.fieldparse.FieldParseStatementVisitor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.show.ShowIndexStatement;
import net.sf.jsqlparser.statement.show.ShowTablesStatement;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.upsert.Upsert;

import java.util.*;

/**
 * @author liutangqi
 * @date 2025/7/16 18:12
 */
@Slf4j
public class FieldDefaultStatementVisitor implements StatementVisitor {
    /**
     * 处理完成后的sql
     */
    @Getter
    private String resultSql;

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

    }

    @Override
    public void visit(Update update) {
        //1.解析涉及到的表拥有的全部字段信息
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        update.accept(fpStatementVisitor);

        //2. 处理updateSet语句
        FieldDefaultVisitorUtil.updateSets(update.getUpdateSets(), fpStatementVisitor, SqlCommandEnum.UPDATE);

        //3.处理结果赋值
        this.resultSql = update.toString();
    }

    @Override
    public void visit(Insert insert) {
        //1.特殊语法处理,完善并校验insert语句缺失的字段
        JsqlparserUtil.perfectColumn(insert);

        //2.解析insert的表
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        insert.accept(fpStatementVisitor);

        //4.处理insert into table(字段) 语法中的字段部分, (依次解析获取每个字段上面标注的@FieldDefault信息，如果设置了默认值的字段没有在insert into(字段) 中出现，则手动添加) 标准的insert语法才做处理 （备注：insert into set这种非标准的sql的话，insert.getSelect()是null ）
        //下标是字段的顺序，从0开始，存的值是对应字段头上的@FieldDefault
        List<FieldDefault> insertFieldDefaultColumns = FieldDefaultVisitorUtil.insertFieldDefaultColumns(insert.getColumns(), insert.getTable());

        //5.处理select的部分（insert into table(xxx) values (values值的部分)）
        Optional.ofNullable(insert.getSelect())
                .ifPresent(p -> p.accept(FieldDefaultSelectVisitor.newInstanceCurLayer(fpStatementVisitor, insertFieldDefaultColumns, true)));

        //6. 解析出insert的表的字段信息
        FieldParseParseTableFromItemVisitor insertFPFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        insert.getTable().accept(insertFPFromItemVisitor);

        //7. 基于insert的表字段信息，处理ON DUPLICATE KEY UPDATE 部分 （注意：这里的部分其实是进行了数据修改的，不是新增）
        FieldDefaultVisitorUtil.updateSets(insert.getDuplicateUpdateSets(), insertFPFromItemVisitor, SqlCommandEnum.UPDATE);

        //8.基于insert的表字段信息，处理 insert set语法部分 （注意：这里的部分其实还是进行的数据新增）
        FieldDefaultVisitorUtil.updateSets(insert.getSetUpdateSets(), insertFPFromItemVisitor, SqlCommandEnum.INSERT);

        //9.基于insert的表字段信息，处理 ON CONFLICT UPDATE (注意：这里的部分其实也是数据修改，不是新增)
        InsertConflictAction conflictAction = insert.getConflictAction();
        if (conflictAction != null) {
            FieldDefaultVisitorUtil.updateSets(conflictAction.getUpdateSets(), insertFPFromItemVisitor, SqlCommandEnum.UPDATE);
        }

        //10.结果集赋值
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
        //1.获取MERGE目标表
        Table table = merge.getTable();

        //2.获取目标表字段对应的默认值信息
        Map<String, FieldDefault> stringFieldDefaultMap = TableCache.getTableFieldDefaultInfo().get(table.getName());
        if (CollectionUtil.isEmpty(stringFieldDefaultMap)) {
            this.resultSql = merge.toString();
            return;
        }

        //3.解析目标表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor fieldParseTable = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        table.accept(fieldParseTable);

        //4.分别按UPDATE和INSERT语义处理matched/not matched分支
        FieldDefaultMergeOperationVisitor mergeOperationVisitor = FieldDefaultMergeOperationVisitor.newInstanceCurLayer(fieldParseTable, stringFieldDefaultMap);
        List<MergeOperation> operations = Optional.ofNullable(merge.getOperations()).orElse(CollectionUtils.EMPTY_LIST);
        for (MergeOperation operation : operations) {
            operation.accept(mergeOperationVisitor);
        }

        //5.处理结果赋值
        this.resultSql = merge.toString();

    }

    @Override
    public void visit(Select select) {

    }

    @Override
    public void visit(Upsert upsert) {
        //1.获取目标表字段对应的默认值信息
        Table table = upsert.getTable();
        Map<String, FieldDefault> stringFieldDefaultMap = TableCache.getTableFieldDefaultInfo().get(table.getName());
        if (CollectionUtil.isEmpty(stringFieldDefaultMap)) {
            this.resultSql = upsert.toString();
            return;
        }

        //2.解析upsert语句
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        upsert.accept(fpStatementVisitor);

        //3.按INSERT语义处理UPSERT VALUES/SELECT中的字段和值
        List<FieldDefault> insertFieldDefaultColumns = FieldDefaultVisitorUtil.upsertFieldDefaultColumns(upsert, stringFieldDefaultMap);
        Select select = upsert.getSelect();
        if (select != null) {
            FieldDefaultSelectVisitor fdSelectVisitor = FieldDefaultSelectVisitor.newInstanceCurLayer(fpStatementVisitor, insertFieldDefaultColumns, true);
            select.accept(fdSelectVisitor);
        }

        //4.REPLACE SET属于数据新增，显式冲突更新分支属于数据修改
        FieldDefaultVisitorUtil.updateSets(upsert.getUpdateSets(), fpStatementVisitor, SqlCommandEnum.INSERT);
        FieldDefaultVisitorUtil.updateSets(upsert.getDuplicateUpdateSets(), fpStatementVisitor, SqlCommandEnum.UPDATE);

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
