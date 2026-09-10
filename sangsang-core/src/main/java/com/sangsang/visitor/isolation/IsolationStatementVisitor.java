package com.sangsang.visitor.isolation;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.visitor.FieldParseVisitorUtil;
import com.sangsang.util.visitor.IsolationVisitorUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import lombok.Getter;
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
import net.sf.jsqlparser.statement.refresh.RefreshMaterializedViewStatement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.show.ShowIndexStatement;
import net.sf.jsqlparser.statement.show.ShowTablesStatement;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.upsert.Upsert;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 处理数据隔离
 *
 * @author liutangqi
 * @date 2025/6/13 13:29
 */
public class IsolationStatementVisitor implements StatementVisitor {
    /**
     * 处理完的sql
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
        //1.未开启DML语句的支持，则不处理
        if (!TableCache.getCurConfig().getIsolation().isSupportDML()) {
            return;
        }

        //2.解析涉及到的表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();

        //2.1 update的表
        Table table = delete.getTable();
        Optional.ofNullable(table).ifPresent(p -> p.accept(fieldParseTableFromItemVisitor));

        //2.2 using中的表
        List<Table> usingTables = Optional.ofNullable(delete.getUsingList()).orElse(new ArrayList<>());
        for (Table usingTable : usingTables) {
            Optional.ofNullable(usingTable).ifPresent(p -> p.accept(fieldParseTableFromItemVisitor));
        }

        //2.3 DELETE目标表列表
        List<Table> targetTables = Optional.ofNullable(delete.getTables()).orElse(new ArrayList<>());
        for (Table targetTable : targetTables) {
            Optional.ofNullable(targetTable).ifPresent(p -> p.accept(fieldParseTableFromItemVisitor));
        }

        //2.4 join的表
        FieldParseVisitorUtil.joins(delete.getJoins(), fieldParseTableFromItemVisitor);

        //3.特殊语法数据隔离处理
        //3.1 joins 存在的子查询
        IsolationVisitorUtil.joins(delete.getJoins(), IsolationFromItemVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor));

        //3.2 CTE中的查询也需要进行数据隔离
        IsolationSelectVisitor isolationSelectVisitor = IsolationSelectVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor);
        IsolationVisitorUtil.cte(delete.getWithItemsList(), isolationSelectVisitor);

        //3.3 Order by 中可能存在子查询
        IsolationVisitorUtil.orderByElements(delete.getOrderByElements(), IsolationExpressionVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor));

        //4.处理where的条件
        Optional.ofNullable(JsqlparserUtil.isolationWhere(delete.getWhere(), fieldParseTableFromItemVisitor))
                .ifPresent(p -> delete.setWhere(p));

        //5.处理结果赋值
        this.resultSql = delete.toString();
    }

    @Override
    public void visit(Update update) {
        //1.未开启DML语句的支持，则不处理
        if (!TableCache.getCurConfig().getIsolation().isSupportDML()) {
            return;
        }

        //2.解析涉及到的表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();

        //2.1 update的表
        Table table = update.getTable();
        table.accept(fieldParseTableFromItemVisitor);

        //2.2 join的内容
        FieldParseVisitorUtil.joins(update.getStartJoins(), fieldParseTableFromItemVisitor);
        FieldParseVisitorUtil.joins(update.getJoins(), fieldParseTableFromItemVisitor);

        //2.3 from中的表 栗如：UPDATE tb_user tu SET tu.phone = su.mobile FROM sys_user su WHERE tu.id = su.id ，这里是可以接from的
        if (update.getFromItem() != null) {
            update.getFromItem().accept(fieldParseTableFromItemVisitor);
        }

        //3.处理特殊语法的数据权限隔离
        //3.1 CTE中的查询进行数据隔离
        IsolationSelectVisitor isolationSelectVisitor = IsolationSelectVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor);
        IsolationVisitorUtil.cte(update.getWithItemsList(), isolationSelectVisitor);

        //3.2 join的表 (不同数据库的join解析有些许差异，有的在startJoins中，有的在joins中)
        IsolationFromItemVisitor isolationFromItemVisitor = IsolationFromItemVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor);
        IsolationVisitorUtil.joins(update.getStartJoins(), isolationFromItemVisitor);
        IsolationVisitorUtil.joins(update.getJoins(), isolationFromItemVisitor);

        //3.3 from中的表 栗如：UPDATE tb_user tu SET tu.phone = su.mobile FROM sys_user su WHERE tu.id = su.id ，这里是可以接from的
        if (update.getFromItem() != null) {
            update.getFromItem().accept(isolationFromItemVisitor);
        }

        //3.4 SET表达式中的标量子查询、EXISTS等嵌套查询
        IsolationExpressionVisitor isolationExpressionVisitor = IsolationExpressionVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor);
        IsolationVisitorUtil.updateSets(update.getUpdateSets(), isolationExpressionVisitor);

        //3.5 ORDER BY中的子查询
        IsolationVisitorUtil.orderByElements(update.getOrderByElements(), isolationExpressionVisitor);

        //4.处理where的条件
        Optional.ofNullable(JsqlparserUtil.isolationWhere(update.getWhere(), fieldParseTableFromItemVisitor))
                .ifPresent(p -> update.setWhere(p));

        //5.处理结果赋值
        this.resultSql = update.toString();
    }

    @Override
    public void visit(Insert insert) {
        //1.未开启DML语句的支持，则不处理
        if (!TableCache.getCurConfig().getIsolation().isSupportDML()) {
            return;
        }

        //2.处理 Insert-Select 语句，括号和非括号形式的 select 都可能携带需要隔离的来源表  insert( select) 或者 insert select 有括号和没括号
        Select select = insert.getSelect();
        if (select != null) {
            //2.1 解析当前sql拥有的全部字段信息
            FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
            select.accept(fieldParseTableSelectVisitor);

            //2.2 进行权限隔离改造
            IsolationSelectVisitor ilSelectVisitor = IsolationSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor);
            select.accept(ilSelectVisitor);
        }

        //3.处理结果赋值
        insert.setSelect(select);
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

    }

    @Override
    public void visit(Select select) {
        //1.解析当前sql拥有的全部字段信息
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
        select.accept(fieldParseTableSelectVisitor);

        //2.进行权限隔离改造
        IsolationSelectVisitor ilSelectVisitor = IsolationSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor);
        select.accept(ilSelectVisitor);

        //3.处理结果赋值
        this.resultSql = select.toString();
    }

    @Override
    public void visit(Upsert upsert) {

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
