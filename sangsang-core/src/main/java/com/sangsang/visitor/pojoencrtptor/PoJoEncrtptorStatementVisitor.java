package com.sangsang.visitor.pojoencrtptor;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldEncryptorInfoDto;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.domain.wrapper.LayerHashMapWrapper;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.ExpressionsUtil;
import com.sangsang.util.visitor.EncryptorPojoVisitorUtil;
import com.sangsang.util.visitor.FieldParseVisitorUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import lombok.Getter;
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
import net.sf.jsqlparser.statement.merge.MergeDelete;
import net.sf.jsqlparser.statement.merge.MergeInsert;
import net.sf.jsqlparser.statement.merge.MergeOperation;
import net.sf.jsqlparser.statement.merge.MergeUpdate;
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
     * key: 占位符FieldConstant.PLACEHOLDER + 0开始的自增序号  （这个在解析前，我们会将？的占位符统一替换成这个格式的占位符）
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
        FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        //2.1 from 后的表
        Table table = delete.getTable();
        table.accept(fieldParseTableFromItemVisitor);

        //2.2 using 后的表
        FieldParseVisitorUtil.usingList(delete.getUsingList(), fieldParseTableFromItemVisitor);

        //2.3 join的表
        FieldParseVisitorUtil.joins(delete.getJoins(), fieldParseTableFromItemVisitor);

        //3.处理join占位符
        EncryptorPojoVisitorUtil.joins(delete.getJoins(), fieldParseTableFromItemVisitor, this.getPlaceholderColumnTableMap());

        //4.处理where条件中的占位符
        EncryptorPojoVisitorUtil.expression(delete.getWhere(), fieldParseTableFromItemVisitor, this.getPlaceholderColumnTableMap());
    }

    @Override
    public void visit(Update update) {
        //1.初始化占位符解析的结果集
        this.placeholderColumnTableMap = new HashMap<>();

        //2.解析涉及到的表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();

        //2.1 update的表
        Table table = update.getTable();
        table.accept(fieldParseTableFromItemVisitor);

        //2.2 直接jjoin的表
        FieldParseVisitorUtil.joins(update.getStartJoins(), fieldParseTableFromItemVisitor);

        //2.3 join的表
        FieldParseVisitorUtil.joins(update.getJoins(), fieldParseTableFromItemVisitor);

        //2.4 from后的表
        FromItem fromItem = update.getFromItem();
        if (fromItem != null) {
            fromItem.accept(fieldParseTableFromItemVisitor);
        }

        //3.解析update后直接join的表及on条件的加密情况
        EncryptorPojoVisitorUtil.joins(update.getStartJoins(), fieldParseTableFromItemVisitor, this.getPlaceholderColumnTableMap());

        //4.解析from后join的表及on条件的加密情况
        EncryptorPojoVisitorUtil.joins(update.getJoins(), fieldParseTableFromItemVisitor, this.getPlaceholderColumnTableMap());

        //5.解析where条件的加密情况
        EncryptorPojoVisitorUtil.expression(update.getWhere(), fieldParseTableFromItemVisitor, this.getPlaceholderColumnTableMap());

        //6.解析set的加密情况
        EncryptorPojoVisitorUtil.updateSets(update.getUpdateSets(), fieldParseTableFromItemVisitor, this.getPlaceholderColumnTableMap());
    }

    @Override
    public void visit(Insert insert) {
        //1.insert 的表
        Table table = insert.getTable();

        //2.解析当前insert字段所属的表结构信息
        //2.1 获取当前insert语句中的所有字段
        List<Column> columns = insert.getColumns();
        //2.2 当遇到了insert into 表 values(xxx)这种语法的时候，如果没有开启自动补全表结构的话，我们是无法得知字段顺序，是无法处理这种表结构的，输出警告日志，便于客户快速定位
        if (CollectionUtils.isEmpty(columns) && !TableCache.getCurConfig().isAutoFill()) {
            log.warn("【sangsang】insert 语句未指定表字段顺序，且当前未开启自动读取表结构，无法获取表字段顺序。此情况可能导致数据错误！！！请规范语法或开启 sangsang.autoFill=true  原sql:{}", insert.toString());
            return;
        }
        //2.3 如果是insert into 表 values(xxx)这种语法 并且开启了自动补全，我们将表字段进行补全
        if (CollectionUtils.isEmpty(columns) && TableCache.getCurConfig().isAutoFill()) {
            List<String> fieldLinkedList = TableCache.getTableFieldMap().get(table.getName());
            columns = fieldLinkedList.stream().map(m -> ExpressionsUtil.buildColumn(m, table.getName())).collect(Collectors.toList());
            insert.setColumns(new ExpressionList(columns));
        }
        //2.4 将insert的所有字段格式进行转换
        List<FieldInfoDto> fieldInfoDtos = columns.stream().map(m -> FieldInfoDto.builder().columnName(m.getColumnName()).sourceColumn(m.getColumnName()).sourceTableName(table.getName()).fromSourceTable(true).rowNumber(false).build()).collect(Collectors.toList());
        //2.5 将转换后的字段信息维护成 layerFieldTableMap 的数据格式
        Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap = new LayerHashMapWrapper();
        Map<String, List<FieldInfoDto>> fieldTableMap = new FieldHashMapWrapper<>();
        fieldTableMap.put(table.getName(), fieldInfoDtos);
        layerFieldTableMap.put(NumberConstant.ONE, fieldTableMap);

        //3.存放占位符信息的Map初始化
        this.placeholderColumnTableMap = new HashMap<>();

        //4.处理select
        Select select = insert.getSelect();
        if (select != null) {
            //4.1 解析当前查询语句的每层表的全部字段(注意：将insert的表的字段解析结果和select的表字段合并在一起，这样下游根据上游字段进行对比时，才知道占位符对应的具体的字段所属表信息)
            FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer(null, layerFieldTableMap);
            select.accept(fieldParseTableSelectVisitor);

            //4.2 处理占位符信息（注意：insert 语句的前后字段有对应关系，所以这里把insert前面的字段传递给后面的visitor）
            PlaceholderSelectVisitor selectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor, this.getPlaceholderColumnTableMap(), columns);
            select.accept(selectVisitor);
        }

        //5.解析insert目标表拥有的全部字段信息
        FieldParseParseTableFromItemVisitor insertFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceFirstLayer();
        table.accept(insertFromItemVisitor);

        //6.处理 insert into set
        EncryptorPojoVisitorUtil.updateSets(insert.getSetUpdateSets(), insertFromItemVisitor, this.getPlaceholderColumnTableMap());

        //7.处理MySQL ON DUPLICATE KEY UPDATE分支中的占位符
        EncryptorPojoVisitorUtil.updateSets(insert.getDuplicateUpdateSets(), insertFromItemVisitor, this.getPlaceholderColumnTableMap());

        //8.处理PostgreSQL ON CONFLICT DO UPDATE分支中的占位符
        InsertConflictAction conflictAction = insert.getConflictAction();
        if (conflictAction != null) {
            //8.1 处理 DO UPDATE分支
            EncryptorPojoVisitorUtil.updateSets(conflictAction.getUpdateSets(), insertFromItemVisitor, this.getPlaceholderColumnTableMap());

            //8.2 DO UPDATE分支可以继续携带where条件，该条件中的占位符同样需要解析
            Expression conflictWhere = conflictAction.getWhereExpression();
            if (conflictWhere != null) {
                PlaceholderExpressionVisitor conflictWhereVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(insertFromItemVisitor, this.getPlaceholderColumnTableMap());
                conflictWhere.accept(conflictWhereVisitor);
            }
        }
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
        List<MergeOperation> operations = Optional.ofNullable(merge.getOperations()).orElse(new ArrayList<>());
        for (MergeOperation operation : operations) {
            if (operation instanceof MergeUpdate) {
                MergeUpdate mergeUpdate = (MergeUpdate) operation;
                EncryptorPojoVisitorUtil.updateSets(mergeUpdate.getUpdateSets(), fieldParseTable, this.getPlaceholderColumnTableMap());
                EncryptorPojoVisitorUtil.expression(mergeUpdate.getAndPredicate(), fieldParseTable, this.getPlaceholderColumnTableMap());
                EncryptorPojoVisitorUtil.expression(mergeUpdate.getWhereCondition(), fieldParseTable, this.getPlaceholderColumnTableMap());
                EncryptorPojoVisitorUtil.expression(mergeUpdate.getDeleteWhereCondition(), fieldParseTable, this.getPlaceholderColumnTableMap());
            }
            if (operation instanceof MergeInsert) {
                MergeInsert mergeInsert = (MergeInsert) operation;
                EncryptorPojoVisitorUtil.columnsAndValues(mergeInsert.getColumns(), mergeInsert.getValues(), targetFieldParseTable, this.getPlaceholderColumnTableMap());
                EncryptorPojoVisitorUtil.expression(mergeInsert.getAndPredicate(), fieldParseTable, this.getPlaceholderColumnTableMap());
                EncryptorPojoVisitorUtil.expression(mergeInsert.getWhereCondition(), fieldParseTable, this.getPlaceholderColumnTableMap());
            }
            if (operation instanceof MergeDelete) {
                MergeDelete mergeDelete = (MergeDelete) operation;
                EncryptorPojoVisitorUtil.expression(mergeDelete.getAndPredicate(), fieldParseTable, this.getPlaceholderColumnTableMap());
            }
        }

    }

    @Override
    public void visit(Select select) {
        //1.解析select拥有的字段对应的表结构信息
        //1.1解析当前sql拥有的全部字段信息
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
        select.accept(fieldParseTableSelectVisitor);

        //1.2.获取sql 查询的所有字段
        Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap = fieldParseTableSelectVisitor.getLayerSelectTableFieldMap();
        List<FieldInfoDto> selectFiles = layerSelectTableFieldMap.getOrDefault(NumberConstant.ONE, new FieldHashMapWrapper<>())
                .values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        //1.3.将每个字段从实体类上找到标注的@FieldEncryptor 注解
        List<FieldEncryptorInfoDto> fieldInfos = selectFiles.stream()
                .map(m -> FieldEncryptorInfoDto.builder()
                        .columnName(m.getColumnName())
                        .sourceColumn(m.getSourceColumn())
                        .sourceTableName(m.getSourceTableName())
                        .fieldEncryptor(TableCache.getTableFieldEncryptInfo().getOrDefault(m.getSourceTableName(), new FieldHashMapWrapper<>()).get(m.getSourceColumn()))
                        .build()
                ).collect(Collectors.toList());

        //1.4.结果集赋值
        this.fieldEncryptorInfos.addAll(fieldInfos);

        //2.将#{}占位符和数据库表结构字段对应起来
        //2.1开始解析
        PlaceholderSelectVisitor placeholderSelectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor, null);
        select.accept(placeholderSelectVisitor);
        //2.2结果赋值
        this.placeholderColumnTableMap = placeholderSelectVisitor.getPlaceholderColumnTableMap();

    }

    @Override
    public void visit(Upsert upsert) {

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
