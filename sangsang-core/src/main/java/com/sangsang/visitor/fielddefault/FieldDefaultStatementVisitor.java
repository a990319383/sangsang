package com.sangsang.visitor.fielddefault;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.sangsang.cache.fielddefault.FieldDefaultInstanceCache;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.annos.fielddefault.FieldDefault;
import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.domain.enums.SqlCommandEnum;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.ExpressionsUtil;
import com.sangsang.util.visitor.FieldDefaultVisitorUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
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
import net.sf.jsqlparser.statement.refresh.RefreshMaterializedViewStatement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.show.ShowIndexStatement;
import net.sf.jsqlparser.statement.show.ShowTablesStatement;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.upsert.Upsert;

import java.util.*;
import java.util.stream.Collectors;

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
        FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();

        //update的表
        Table table = update.getTable();
        table.accept(fieldParseTableFromItemVisitor);

        //join的表
        List<Join> joins = Optional.ofNullable(update.getStartJoins()).orElse(new ArrayList<>());
        for (Join join : joins) {
            join.getRightItem().accept(fieldParseTableFromItemVisitor);
        }

        //2. 处理updateSet语句
        FieldDefaultVisitorUtil.updateSets(update.getUpdateSets(), fieldParseTableFromItemVisitor.getLayerFieldTableMap().get(NumberConstant.ONE), SqlCommandEnum.UPDATE);

        //3.处理结果赋值
        this.resultSql = update.toString();
    }

    @Override
    public void visit(Insert insert) {
        //1.获取insert的表
        Table table = insert.getTable();

        //2.获取当前表字段需要设置默认值的字段信息
        Map<String, FieldDefault> stringFieldDefaultMap = TableCache.getTableFieldDefaultInfo().get(table.getName());

        //3.不处理的场景校验
        //3.1当前insert的表不涉及维护默认值，则不做处理
        if (CollectionUtil.isEmpty(stringFieldDefaultMap)) {
            return;
        }
        //3.2 sql中存在 * 不做处理  (insert into table (xxx) (select * from) 这里* 没办法做字段对应)
        //备注：其实开启了自动获取表结构，但是下游的select * from的表的表结构目前是没有存储的，如果要兼容这种语法需要花费较大空间存储全库表结构，目前暂不支持
        if (insert.toString().contains(SymbolConstant.START)) {
            log.warn("【sangsang】insert 存在*。此情况无法设置默认值!!!请规范语法!!!  原sql:{}", insert.toString());
            return;
        }

        //4.处理insert into table(字段) 语法中的字段部分, (依次解析获取每个字段上面标注的@FieldDefault信息，如果设置了默认值的字段没有在insert into(字段) 中出现，则手动添加) 标准的insert语法才做处理 （备注：insert into set这种非标准的sql的话，insert.getSelect()是null ）
        //下标是字段的顺序，从0开始，存的值是对应字段头上的@FieldDefault
        List<FieldDefault> insertFieldDefaultColumns = FieldDefaultVisitorUtil.insertFieldDefaultColumns(insert, stringFieldDefaultMap);

        //5.处理select的部分（insert into table(xxx) values (values值的部分)）
        Select select = insert.getSelect();
        if (select != null) {
            //5.1 解析select部分
            FieldParseParseTableSelectVisitor fPSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
            select.accept(fPSelectVisitor);
            //5.2.处理默认值
            FieldDefaultSelectVisitor fdSelectVisitor = FieldDefaultSelectVisitor.newInstanceCurLayer(fPSelectVisitor, insertFieldDefaultColumns);
            select.accept(fdSelectVisitor);
        }

        //6. 解析出insert的表的字段信息
        FieldParseParseTableFromItemVisitor insertFPFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        insert.getTable().accept(insertFPFromItemVisitor);

        //7. 基于insert的表字段信息，处理ON DUPLICATE KEY UPDATE 部分 （注意：这里的部分其实是进行了数据修改的，不是新增）
        FieldDefaultVisitorUtil.updateSets(insert.getDuplicateUpdateSets(), insertFPFromItemVisitor.getLayerFieldTableMap().get(NumberConstant.ONE), SqlCommandEnum.UPDATE);

        //8.基于insert的表字段信息，处理 insert set语法部分 （注意：这里的部分其实还是进行的数据新增）
        FieldDefaultVisitorUtil.updateSets(insert.getSetUpdateSets(), insertFPFromItemVisitor.getLayerFieldTableMap().get(NumberConstant.ONE), SqlCommandEnum.INSERT);

        //9.基于insert的表字段信息，处理 ON CONFLICT UPDATE (注意：这里的部分其实也是数据修改，不是新增)
        InsertConflictAction conflictAction = insert.getConflictAction();
        if (conflictAction != null) {
            FieldDefaultVisitorUtil.updateSets(conflictAction.getUpdateSets(), insertFPFromItemVisitor.getLayerFieldTableMap().get(NumberConstant.ONE), SqlCommandEnum.UPDATE);
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

    }

    @Override
    public void visit(Select select) {

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
