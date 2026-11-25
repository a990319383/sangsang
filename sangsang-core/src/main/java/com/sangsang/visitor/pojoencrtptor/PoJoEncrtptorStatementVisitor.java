package com.sangsang.visitor.pojoencrtptor;

import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldEncryptorInfoDto;
import com.sangsang.domain.dto.UpstreamExpressionDto;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.visitor.EncryptorPojoVisitorUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import com.sangsang.visitor.fieldparse.FieldParseStatementVisitor;
import lombok.Getter;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 通过对pojo进行加解密的sql解析入口
 * 通过这里的解析，主要目的
 * 1.获取到select的每一项对应数据库的表和字段
 * 2.将每个#{}占位符合数据库的表，字段对应上
 *
 * @author liutangqi
 * @date 2024/7/6 13:22
 */
@Slf4j
public class PoJoEncrtptorStatementVisitor implements StatementVisitor {
    /**
     * 当前sql涉及到的字段以及字段的所属表结构信息
     **/
    @Getter
    private List<FieldEncryptorInfoDto> fieldEncryptorInfos = new ArrayList<>();

    /**
     * 当前占位符对应的数据库表，字段信息
     * key: 占位符FieldConstant.PLACEHOLDER + 0开始的自增序号
     * value: 这个字段所属的表字段
     */
    @Getter
    private Map<String, ColumnTableDto> placeholderColumnTableMap;

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
        //1.初始化占位符解析的结果集
        this.placeholderColumnTableMap = new HashMap<>();

        //2.解析涉及到的表拥有的全部字段信息
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        delete.accept(fpStatementVisitor);

        //3.处理join占位符
        EncryptorPojoVisitorUtil.joins(delete.getJoins(), fpStatementVisitor, this.getPlaceholderColumnTableMap());

        //4.处理where条件中的占位符
        EncryptorPojoVisitorUtil.expression(delete.getWhere(), fpStatementVisitor, this.getPlaceholderColumnTableMap());

        //5.存储最外层DML返回结果
        this.fieldEncryptorInfos.addAll(EncryptorPojoVisitorUtil.buildFirstLayerSelectTableFieldMap(fpStatementVisitor));
    }

    @Override
    public void visit(Update update) {
        //1.初始化占位符解析的结果集
        this.placeholderColumnTableMap = new HashMap<>();

        //2.解析涉及到的表拥有的全部字段信息
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        update.accept(fpStatementVisitor);

        //3.解析update后直接join的表及on条件的加密情况
        EncryptorPojoVisitorUtil.joins(update.getStartJoins(), fpStatementVisitor, this.getPlaceholderColumnTableMap());

        //4.解析from后join的表及on条件的加密情况
        EncryptorPojoVisitorUtil.joins(update.getJoins(), fpStatementVisitor, this.getPlaceholderColumnTableMap());

        //5.解析from子查询中的占位符
        Optional.ofNullable(update.getFromItem())
                .ifPresent(p -> p.accept(PlaceholderSelectFromItemVisitor.newInstanceCurLayer(fpStatementVisitor, this.getPlaceholderColumnTableMap())));

        //6.解析where条件的加密情况
        EncryptorPojoVisitorUtil.expression(update.getWhere(), fpStatementVisitor, this.getPlaceholderColumnTableMap());

        //7.解析set的加密情况
        EncryptorPojoVisitorUtil.updateSets(update.getUpdateSets(), fpStatementVisitor, this.getPlaceholderColumnTableMap());

        //8.存储最外层DML返回结果
        this.fieldEncryptorInfos.addAll(EncryptorPojoVisitorUtil.buildFirstLayerSelectTableFieldMap(fpStatementVisitor));
    }

    @Override
    public void visit(Insert insert) {
        //1.特殊语法处理，如果insert into 后面的表没有跟具体字段的话，根据情况进行完善
        JsqlparserUtil.perfectColumn(insert);

        //2.解析sql
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        insert.accept(fpStatementVisitor);

        //3.存放占位符信息的Map初始化
        this.placeholderColumnTableMap = new HashMap<>();

        //5. 处理占位符信息（注意1：insert 语句的前后字段有对应关系，所以这里把insert前面的字段传递给后面的visitor 注意2：这里select是子查询，属于下一层作用域）
        Select select = insert.getSelect();
        if (select != null) {
            List<Column> columns = insert.getColumns().stream().collect(Collectors.toList());
            List<UpstreamExpressionDto> upstreamExpressionDtos = JsqlparserUtil.buildUpstreamExpressionDto(fpStatementVisitor, columns);
            //注意：这里是下一层，因为fieldParser解析的时候，将这里的select作为下一层来解析的
            PlaceholderSelectVisitor selectVisitor = PlaceholderSelectVisitor.newInstanceNextLayer(fpStatementVisitor, this.getPlaceholderColumnTableMap(), upstreamExpressionDtos);
            select.accept(selectVisitor);
        }

        //6.处理 insert into set
        EncryptorPojoVisitorUtil.updateSets(insert.getSetUpdateSets(), fpStatementVisitor, this.getPlaceholderColumnTableMap());

        //7.处理MySQL ON DUPLICATE KEY UPDATE分支中的占位符
        EncryptorPojoVisitorUtil.updateSets(insert.getDuplicateUpdateSets(), fpStatementVisitor, this.getPlaceholderColumnTableMap());

        //8.处理PostgreSQL ON CONFLICT DO UPDATE分支中的占位符
        InsertConflictAction conflictAction = insert.getConflictAction();
        if (conflictAction != null) {
            //8.1 处理 DO UPDATE分支
            EncryptorPojoVisitorUtil.updateSets(conflictAction.getUpdateSets(), fpStatementVisitor, this.getPlaceholderColumnTableMap());

            //8.2 DO UPDATE分支可以继续携带where条件，该条件中的占位符同样需要解析
            Expression conflictWhere = conflictAction.getWhereExpression();
            if (conflictWhere != null) {
                PlaceholderExpressionVisitor conflictWhereVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(fpStatementVisitor, this.getPlaceholderColumnTableMap());
                conflictWhere.accept(conflictWhereVisitor);
            }
        }

        //9.存储最外层DML返回结果
        this.fieldEncryptorInfos.addAll(EncryptorPojoVisitorUtil.buildFirstLayerSelectTableFieldMap(fpStatementVisitor));
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
    public void visit(Statements statements) {

    }

    @Override
    public void visit(Execute execute) {

    }

    @Override
    public void visit(SetStatement setStatement) {

    }

    @Override
    public void visit(ResetStatement resetStatement) {

    }

    @Override
    public void visit(ShowColumnsStatement showColumnsStatement) {

    }

    @Override
    public void visit(ShowIndexStatement showIndex) {

    }

    @Override
    public void visit(ShowTablesStatement showTablesStatement) {

    }

    @Override
    public void visit(Merge merge) {
        //1.初始化占位符解析的结果集
        this.placeholderColumnTableMap = new HashMap<>();

        //2.解析目标表和using来源表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor fieldParseTable = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        merge.getTable().accept(fieldParseTable);
        FromItem fromItem = merge.getFromItem();
        if (fromItem != null) {
            fromItem.accept(fieldParseTable);
        }

        //3.处理on条件中的占位符
        EncryptorPojoVisitorUtil.expression(merge.getOnCondition(), fieldParseTable, this.getPlaceholderColumnTableMap());

        //4.单独解析目标表，用于处理not matched insert分支中没有表别名的目标字段
        FieldParseParseTableFromItemVisitor targetFieldParseTable = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        merge.getTable().accept(targetFieldParseTable);

        //5.处理matched/not matched的全部分支
        PojoEncryptorMergeOperationVisitor mergeOperationVisitor = PojoEncryptorMergeOperationVisitor.newInstanceCurLayer(fieldParseTable, targetFieldParseTable, this.getPlaceholderColumnTableMap());
        List<MergeOperation> operations = Optional.ofNullable(merge.getOperations()).orElse(CollectionUtils.EMPTY_LIST);
        for (MergeOperation operation : operations) {
            operation.accept(mergeOperationVisitor);
        }

    }

    @Override
    public void visit(Select select) {
        //1.解析select拥有的字段对应的表结构信息
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        select.accept(fpStatementVisitor);

        //2.将#{}占位符和数据库表结构字段对应起来
        //2.1开始解析
        PlaceholderSelectVisitor placeholderSelectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(fpStatementVisitor, null);
        select.accept(placeholderSelectVisitor);
        //2.2结果赋值
        this.placeholderColumnTableMap = placeholderSelectVisitor.getPlaceholderColumnTableMap();

        //3.存储最外层的返回结果
        this.fieldEncryptorInfos.addAll(EncryptorPojoVisitorUtil.buildFirstLayerSelectTableFieldMap(fpStatementVisitor));
    }

    @Override
    public void visit(Upsert upsert) {
        //1.初始化占位符解析的结果集
        this.placeholderColumnTableMap = new HashMap<>();

        //2.解析目标表拥有的全部字段信息
        FieldParseStatementVisitor fpStatementVisitor = FieldParseStatementVisitor.newInstanceFirstLayer();
        upsert.accept(fpStatementVisitor);

        //3.处理REPLACE SET及冲突更新分支中的占位符
        EncryptorPojoVisitorUtil.updateSets(upsert.getUpdateSets(), fpStatementVisitor, this.getPlaceholderColumnTableMap());
        EncryptorPojoVisitorUtil.updateSets(upsert.getDuplicateUpdateSets(), fpStatementVisitor, this.getPlaceholderColumnTableMap());

        //4.处理UPSERT VALUES/SELECT语法中的占位符
        List<Column> columns = upsert.getColumns();
        Select select = upsert.getSelect();
        if (CollectionUtils.isNotEmpty(columns) && select != null) {
            //4.1 构建上游对应表达式
            List<UpstreamExpressionDto> upstreamExpressionDtos = JsqlparserUtil.buildUpstreamExpressionDto(fpStatementVisitor, columns);
            //4.2 开始解析select的占位符情况
            PlaceholderSelectVisitor placeholderSelectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(fpStatementVisitor, placeholderColumnTableMap, upstreamExpressionDtos);
            select.accept(placeholderSelectVisitor);
        }

    }

    @Override
    public void visit(UseStatement useStatement) {

    }

    @Override
    public void visit(Block block) {

    }

    @Override
    public void visit(DescribeStatement describeStatement) {

    }

    @Override
    public void visit(ExplainStatement explainStatement) {

    }

    @Override
    public void visit(ShowStatement showStatement) {

    }

    @Override
    public void visit(DeclareStatement declareStatement) {

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
