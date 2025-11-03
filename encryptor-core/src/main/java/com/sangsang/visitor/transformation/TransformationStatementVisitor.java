package com.sangsang.visitor.transformation;

import com.sangsang.cache.transformation.TransformationInstanceCache;
import com.sangsang.domain.dto.ColumnTransformationDto;
import com.sangsang.util.CollectionUtils;
import com.sangsang.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import com.sangsang.visitor.transformation.wrap.ExpressionWrapper;
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
import net.sf.jsqlparser.statement.merge.Merge;
import net.sf.jsqlparser.statement.refresh.RefreshMaterializedViewStatement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.show.ShowIndexStatement;
import net.sf.jsqlparser.statement.show.ShowTablesStatement;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
import net.sf.jsqlparser.statement.upsert.Upsert;

import java.util.ArrayList;
import java.util.List;
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
        FieldParseParseTableFromItemVisitor fpFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        // from 后的表
        Table table = delete.getTable();
        table.accept(fpFromItemVisitor);
        //join 的表
        List<Join> joins = Optional.ofNullable(delete.getJoins()).orElse(new ArrayList<>());
        for (Join join : joins) {
            FromItem rightItem = join.getRightItem();
            rightItem.accept(fpFromItemVisitor);
        }

        //2.将表进行转换
        TransformationFromItemVisitor tfFromItemVisitor = TransformationFromItemVisitor.newInstanceCurLayer(fpFromItemVisitor);
        table.accept(tfFromItemVisitor);
        for (Join join : joins) {
            join.getRightItem().accept(tfFromItemVisitor);
        }

        //3.处理where条件
        TransformationExpressionVisitor tfExpressionVisitor = TransformationExpressionVisitor.newInstanceCurLayer(fpFromItemVisitor);
        //使用包装类进行转转，额外对整个Expression进行语法转换一次
        Expression tfExp = ExpressionWrapper.wrap(delete.getWhere()).accept(tfExpressionVisitor);
        Optional.ofNullable(tfExp).ifPresent(p -> delete.setWhere(p));

        //4.结果赋值
        this.resultSql = delete.toString();
    }

    @Override
    public void visit(Update update) {
        //1.解析涉及到的表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor fpFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        //update的表
        Table table = update.getTable();
        table.accept(fpFromItemVisitor);
        //join的表
        List<Join> joins = Optional.ofNullable(update.getStartJoins()).orElse(new ArrayList<>());
        for (Join join : joins) {
            join.getRightItem().accept(fpFromItemVisitor);
        }

        //2.将update和join的表进行转换
        TransformationFromItemVisitor tfFromItemVisitor = TransformationFromItemVisitor.newInstanceCurLayer(fpFromItemVisitor);
        table.accept(tfFromItemVisitor);
        //join的表
        for (Join join : joins) {
            join.getRightItem().accept(tfFromItemVisitor);
        }

        //3.处理where条件
        Expression where = update.getWhere();
        if (where != null) {
            TransformationExpressionVisitor tfExpressionVisitor = TransformationExpressionVisitor.newInstanceCurLayer(fpFromItemVisitor);
            //使用包装类进行转转，额外对整个Expression进行语法转换一次
            Expression tfExp = ExpressionWrapper.wrap(where).accept(tfExpressionVisitor);
            Optional.ofNullable(tfExp).ifPresent(p -> update.setWhere(p));
        }

        //4.处理set的值
        List<UpdateSet> updateSets = update.getUpdateSets();
        for (UpdateSet updateSet : updateSets) {
            ExpressionList<Column> columns = updateSet.getColumns();
            ExpressionList<Expression> values = (ExpressionList<Expression>) updateSet.getValues();
            //依次处理每一对值（这里columns 和对应set的values 个数肯定是相等的）
            for (int i = 0; i < columns.size(); i++) {
                //4.1 先对左边的Column进行语法转换
                //这里的column肯定是数据库的字段，所以直接传true
                ColumnTransformationDto columnTransformationDto = new ColumnTransformationDto(columns.get(i), true, true);
                ColumnTransformationDto transformation = TransformationInstanceCache.transformation(columnTransformationDto);
                if (transformation != null) {
                    columns.set(i, transformation.getColumn());
                }
                //4.2对右边的值进行语法转化
                TransformationExpressionVisitor tfExpressionVisitor = TransformationExpressionVisitor.newInstanceCurLayer(fpFromItemVisitor);
                Expression valueExp = values.get(i);
                //使用包装类进行转转，额外对整个Expression进行语法转换一次
                Expression tfExp = ExpressionWrapper.wrap(valueExp).accept(tfExpressionVisitor);
                if (tfExp != null) {
                    values.set(i, tfExp);
                }
            }
        }

        //5.处理结果赋值
        this.resultSql = update.toString();
    }

    @Override
    public void visit(Insert insert) {
        //1.解析select的表字段信息
        FieldParseParseTableSelectVisitor fPSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
        Select select = insert.getSelect();
        select.accept(fPSelectVisitor);

        //2.处理select
        TransformationSelectVisitor tfSelectVisitor = TransformationSelectVisitor.newInstanceCurLayer(fPSelectVisitor);
        select.accept(tfSelectVisitor);

        //3.处理Column
        ExpressionList<Column> columns = insert.getColumns();
        if (CollectionUtils.isNotEmpty(columns)) {
            for (int i = 0; i < columns.size(); i++) {
                //这里的column肯定是数据库的字段，所以直接传true
                ColumnTransformationDto columnTransformationDto = new ColumnTransformationDto(columns.get(i), true, true);
                ColumnTransformationDto transformation = TransformationInstanceCache.transformation(columnTransformationDto);
                if (transformation != null) {
                    columns.set(i, transformation.getColumn());
                }
            }
        }

        //4.处理table
        Table table = insert.getTable();
        table.accept(TransformationFromItemVisitor.newInstanceCurLayer(tfSelectVisitor));

        //5.结果赋值
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
        //1解析当前sql拥有的全部字段信息
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
        select.accept(fieldParseTableSelectVisitor);

        //2.进行语法转换
        TransformationSelectVisitor selectVisitor = TransformationSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor);
        select.accept(selectVisitor);

        //3.处理好的sql赋值
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
