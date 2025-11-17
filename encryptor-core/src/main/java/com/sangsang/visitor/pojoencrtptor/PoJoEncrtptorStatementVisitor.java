package com.sangsang.visitor.pojoencrtptor;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.FieldEncryptorInfoDto;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
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
    private List<FieldEncryptorInfoDto> fieldEncryptorInfos = new ArrayList<>();

    /**
     * 当前占位符对应的数据库表，字段信息
     * key: 占位符DecryptConstant.PLACEHOLDER + 0开始的自增序号  （这个在解析前，我们会将？的占位符统一替换成这个格式的占位符）
     * value: 这个字段所属的表字段
     */
    private Map<String, ColumnTableDto> placeholderColumnTableMap;

    public List<FieldEncryptorInfoDto> getFieldEncryptorInfos() {
        return fieldEncryptorInfos;
    }

    public Map<String, ColumnTableDto> getPlaceholderColumnTableMap() {
        return placeholderColumnTableMap;
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

        //3.将where 条件进行加密
        PlaceholderExpressionVisitor placeholderWhereExpressionVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor, this.placeholderColumnTableMap);
        where.accept(placeholderWhereExpressionVisitor);

        //4.结果赋值
        this.placeholderColumnTableMap = placeholderWhereExpressionVisitor.getPlaceholderColumnTableMap();
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

        //2.初始化占位符解析的结果集
        this.placeholderColumnTableMap = new HashMap<>();

        //3.加密where 条件的数据
        Expression where = update.getWhere();
        if (where != null) {
            PlaceholderExpressionVisitor dencryptWhereFieldVisitor = PlaceholderExpressionVisitor.newInstanceCurLayer(fieldParseTableFromItemVisitor, this.getPlaceholderColumnTableMap());
            where.accept(dencryptWhereFieldVisitor);
        }

        //4.加密处理set的数据
        List<UpdateSet> updateSets = update.getUpdateSets();
        for (UpdateSet updateSet : updateSets) {
            List<Column> columns = updateSet.getColumns();
            ExpressionList<Expression> expressions = (ExpressionList<Expression>) updateSet.getValues();
            for (int i = 0; i < columns.size(); i++) {
                Column column = columns.get(i);
                Expression expression = expressions.get(i);

                //处理左右两边表达式的占位符信息
                JsqlparserUtil.parseWhereColumTable(fieldParseTableFromItemVisitor.getLayer(),
                        fieldParseTableFromItemVisitor.getLayerFieldTableMap(),
                        column,
                        expression,
                        this.getPlaceholderColumnTableMap());
            }
        }
    }

    @Override
    public void visit(Insert insert) {
        //1.insert 的表
        Table table = insert.getTable();

        //2.解析当前insert字段所属的表结构信息
        //2.1 获取当前insert语句中的所有字段
        List<Column> columns = insert.getColumns();
        if (CollectionUtils.isEmpty(columns)) {
            log.warn("【field-encryptor】insert 语句未指定表字段顺序，不支持自动加解密，请规范语法 原sql:{}", insert.toString());
            return;
        }
        //2.2将insert的所有字段格式进行转换
        Set<FieldInfoDto> fieldInfoDtos = columns.stream()
                .map(m -> new FieldInfoDto(m.getColumnName(), m.getColumnName(), table.getName(), true))
                .collect(Collectors.toSet());
        //2.3 将转换后的字段信息维护成 layerFieldTableMap 的数据格式
        Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap = new HashMap<>();
        Map<String, Set<FieldInfoDto>> fieldTableMap = new FieldHashMapWrapper<>();
        fieldTableMap.put(table.getName(), fieldInfoDtos);
        layerFieldTableMap.put(String.valueOf(NumberConstant.ONE), fieldTableMap);


        //3.存放占位符信息的Map初始化
        this.placeholderColumnTableMap = new HashMap<>();


        //4.处理select
        Select select = insert.getSelect();
        //解析当前查询语句的每层表的全部字段(注意：将insert的表的字段解析结果和select的表字段合并在一起，这样下游根据上游字段进行对比时，才知道占位符对应的具体的字段所属表信息)
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer(null, layerFieldTableMap);
        select.accept(fieldParseTableSelectVisitor);

        //5.解析占位符（注意：insert 语句的前后字段有对应关系，所以这里把insert前面的字段传递给后面的visitor）
        PlaceholderSelectVisitor selectVisitor = PlaceholderSelectVisitor.newInstanceCurLayer(fieldParseTableSelectVisitor, this.getPlaceholderColumnTableMap(), columns);
        select.accept(selectVisitor);

        //6.ON DUPLICATE KEY UPDATE 语法 此语法不用单独处理，即可兼容
//        List<Column> duplicateUpdateColumns = insert.getDuplicateUpdateColumns();
//        List<Expression> duplicateUpdateExpressionList = insert.getDuplicateUpdateExpressionList();
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

    }

    @Override
    public void visit(Select select) {
        //1.解析select拥有的字段对应的表结构信息
        //1.1解析当前sql拥有的全部字段信息
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
        select.accept(fieldParseTableSelectVisitor);

        //1.2.获取sql 查询的所有字段
        Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap = fieldParseTableSelectVisitor.getLayerSelectTableFieldMap();
        List<FieldInfoDto> selectFiles = layerSelectTableFieldMap.getOrDefault(String.valueOf(NumberConstant.ONE), new HashMap<>())
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
