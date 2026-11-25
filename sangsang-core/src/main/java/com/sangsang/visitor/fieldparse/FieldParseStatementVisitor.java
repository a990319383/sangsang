package com.sangsang.visitor.fieldparse;

import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.visitor.FieldParseVisitorUtil;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author liutangqi
 * @date 2026/11/18 13:27
 */
public class FieldParseStatementVisitor extends BaseFieldParseTable implements StatementVisitor {

    /**
     * 获取第一层对象
     *
     * @author liutangqi
     * @date 2026/11/18 13:29
     * @Param []
     **/
    public static FieldParseStatementVisitor newInstanceFirstLayer() {
        return new FieldParseStatementVisitor(
                NumberConstant.ONE,
                null,
                null
        );
    }

    /**
     * 禁止外面通过new 创建对象
     *
     * @param layer
     * @param layerSelectTableFieldMap
     * @param layerFieldTableMap
     * @author liutangqi
     * @date 2026/11/13 14:25
     * @Param [layer, layerSelectTableFieldMap, layerFieldTableMap]
     */
    protected FieldParseStatementVisitor(int layer,
                                         Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap,
                                         Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
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
        FieldParseParseTableFromItemVisitor fPFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceCurLayer(this);
        //1 from 后的表
        Table table = delete.getTable();
        Optional.ofNullable(table).ifPresent(p -> p.accept(fPFromItemVisitor));

        //2 using 后的表
        FieldParseVisitorUtil.usingList(delete.getUsingList(), fPFromItemVisitor);

        //3 join的表
        FieldParseVisitorUtil.joins(delete.getJoins(), fPFromItemVisitor);

        //4.解析DML返回值到layerSelectTableFieldMap
        FieldParseVisitorUtil.dmlResultParseLayerSelect(delete.getReturningClause(), delete.getTable(), this, Optional.ofNullable(delete.getReturningClause()).map(ReturningClause::getDataItems).orElse(CollectionUtils.EMPTY_LIST));
        FieldParseVisitorUtil.dmlResultParseLayerSelect(Optional.ofNullable(delete.getOutputClause()).map(OutputClause::getSelectItemList).orElse(null), delete.getTable(), this, Optional.ofNullable(delete.getOutputClause()).map(OutputClause::getOutputTable).map(Arrays::asList).orElse(CollectionUtils.EMPTY_LIST));
    }

    @Override
    public void visit(Update update) {
        FieldParseParseTableFromItemVisitor fpFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceCurLayer(this);
        //1 update的表
        Table table = update.getTable();
        table.accept(fpFromItemVisitor);

        //2 join的表（不同数据库的join解析有些许差异，有的在startJoins中，有的在joins中）
        FieldParseVisitorUtil.joins(update.getStartJoins(), fpFromItemVisitor);
        FieldParseVisitorUtil.joins(update.getJoins(), fpFromItemVisitor);

        //3 from后的表
        Optional.ofNullable(update.getFromItem()).ifPresent(p -> p.accept(fpFromItemVisitor));

        //4.解析DML返回值到layerSelectTableFieldMap
        FieldParseVisitorUtil.dmlResultParseLayerSelect(update.getReturningClause(), update.getTable(), this, Optional.ofNullable(update.getReturningClause()).map(ReturningClause::getDataItems).orElse(CollectionUtils.EMPTY_LIST));
        FieldParseVisitorUtil.dmlResultParseLayerSelect(Optional.ofNullable(update.getOutputClause()).map(OutputClause::getSelectItemList).orElse(null), update.getTable(), this, Optional.ofNullable(update.getOutputClause()).map(OutputClause::getOutputTable).map(Arrays::asList).orElse(CollectionUtils.EMPTY_LIST));
    }

    @Override
    public void visit(Insert insert) {
        //1.解析table
        Table table = insert.getTable();
        FieldParseParseTableFromItemVisitor fPFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceCurLayer(this);
        table.accept(fPFromItemVisitor);

        //2.解析select (注意：非标准insert 语句 select可能为空)
        FieldParseParseTableSelectVisitor fpTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceNextLayer(fPFromItemVisitor);
        Optional.ofNullable(insert.getSelect())
                .ifPresent(p -> p.accept(fpTableSelectVisitor));

        //3.解析DML返回值到layerSelectTableFieldMap
        FieldParseVisitorUtil.dmlResultParseLayerSelect(insert.getReturningClause(), insert.getTable(), this, Optional.ofNullable(insert.getReturningClause()).map(ReturningClause::getDataItems).orElse(CollectionUtils.EMPTY_LIST));
        FieldParseVisitorUtil.dmlResultParseLayerSelect(Optional.ofNullable(insert.getOutputClause()).map(OutputClause::getSelectItemList).orElse(null), insert.getTable(), this, Optional.ofNullable(insert.getOutputClause()).map(OutputClause::getOutputTable).map(Arrays::asList).orElse(CollectionUtils.EMPTY_LIST));
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
        // 存在目标表和原始表，且update的表还不带别名，这里统一解析需要记录哪个是原始表，哪个是目标表，太麻烦
    }

    @Override
    public void visit(Select select) {
        //解析当前sql拥有的全部字段信息
        FieldParseParseTableSelectVisitor fpSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceCurLayer(this);
        select.accept(fpSelectVisitor);
    }

    @Override
    public void visit(Upsert upsert) {
        //1.解析目标表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor fpFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceCurLayer(this);
        upsert.getTable().accept(fpFromItemVisitor);

        //2.解析select (注意：非标准insert 语句 select可能为空)
        FieldParseParseTableSelectVisitor fpTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceNextLayer(fpFromItemVisitor);
        Optional.ofNullable(upsert.getSelect())
                .ifPresent(p -> p.accept(fpTableSelectVisitor));
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
