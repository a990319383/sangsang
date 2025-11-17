package com.sangsang.visitor.dbencrtptor;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.enums.EncryptorFunctionEnum;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
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
import java.util.Map;
import java.util.Optional;

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
        //1.where 条件 不存在，则不进行加密处理（delete语句主要对delete的条件进行加密）
        Expression where = delete.getWhere();
        if (where == null) {
            return;
        }

        //2.解析涉及到的表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        // from 后的表
        Table table = delete.getTable();
        table.accept(fieldParseTableFromItemVisitor);

        //join 的表
        List<Join> joins = Optional.ofNullable(delete.getJoins()).orElse(new ArrayList<>());
        for (Join join : joins) {
            FromItem rightItem = join.getRightItem();
            rightItem.accept(fieldParseTableFromItemVisitor);
        }

        //3.将where 条件进行解密
        DBDecryptExpressionVisitor sDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor, EncryptorFunctionEnum.DEFAULT_DECRYPTION, null);
        where.accept(sDecryptExpressionVisitor);
        delete.setWhere(Optional.ofNullable(sDecryptExpressionVisitor.getProcessedExpression()).orElse(where));

        //4.结果赋值
        this.resultSql = delete.toString();
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

        //2.解密where 条件的数据
        Expression where = update.getWhere();
        if (where != null) {
            DBDecryptExpressionVisitor expressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor, EncryptorFunctionEnum.DEFAULT_DECRYPTION, null);
            where.accept(expressionVisitor);
            //修改后的where赋值
            update.setWhere(Optional.ofNullable(expressionVisitor.getProcessedExpression()).orElse(where));
        }

        //3.加密处理set的数据
        List<UpdateSet> updateSets = update.getUpdateSets();
        for (UpdateSet updateSet : updateSets) {
            List<Column> columns = updateSet.getColumns();
            ExpressionList<Expression> expressions = (ExpressionList<Expression>) updateSet.getValues();
            //处理每对需要加密的字段
            for (int i = 0; i < columns.size(); i++) {
                Column column = columns.get(i);
                Expression expression = expressions.get(i);
                //左边是否需要加解密
                FieldEncryptor leftFieldEncryptor = JsqlparserUtil.needEncryptFieldEncryptor(column, fieldParseTableFromItemVisitor.getLayer(), fieldParseTableFromItemVisitor.getLayerFieldTableMap());
                //根据左边是否密文存储来对右边进行处理
                DBDecryptExpressionVisitor sDecryptExpressionVisitor = DBDecryptExpressionVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor, EncryptorFunctionEnum.UPSTREAM_COLUMN, leftFieldEncryptor);
                expression.accept(sDecryptExpressionVisitor);
                expressions.set(i, Optional.ofNullable(sDecryptExpressionVisitor.getProcessedExpression()).orElse(expression));
            }
        }

        //5.处理结果赋值
        this.resultSql = update.toString();

    }

    @Override
    public void visit(Insert insert) {
        //1. insert 的表
        Table table = insert.getTable();
        Map<String, FieldEncryptor> fieldEncryptMap = TableCache.getTableFieldEncryptInfo().get(table.getName());

        //2.获取当前第几个字段是需要加密的
        // 需要加密的字段的索引
        List<FieldEncryptor> needEncryptFieldEncryptor = new ArrayList<>();
        //insert 的字段名
        List<Column> columns = insert.getColumns();
        if (CollectionUtils.isEmpty(columns)) {
            log.warn("【field-encryptor】insert 语句未指定表字段顺序，不支持自动加解密，请规范语法 原sql:{}", insert.toString());
            return;
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

        //5.处理好的sql赋值
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
