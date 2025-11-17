package com.sangsang.visitor.fielddefault;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.sangsang.cache.fielddefault.FieldDefaultInstanceCache;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.annos.fielddefault.FieldDefault;
import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.domain.dto.ColumnUniqueDto;
import com.sangsang.domain.enums.SqlCommandEnum;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.domain.wrapper.FieldHashSetWrapper;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.ExpressionsUtil;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
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

        //2.获取当前sql涉及到的表字段需要设置默认值的字段信息
        //2.1过滤出当前层涉及到的所有表字段上面需要设置默认值的字段信息
        Map<ColumnUniqueDto, FieldDefault> columnFieldDefaultMap = JsqlparserUtil.filterFieldDefault(fieldParseTableFromItemVisitor.getLayerFieldTableMap().get(String.valueOf(NumberConstant.ONE)),
                SqlCommandEnum.UPDATE);
        //2.2过滤出其中发生了字段变更的表名，当字段没有所属的表别名，说明只有一张表，就用update的表名
        List<UpdateSet> updateSets = update.getUpdateSets();
        Set<String> updateTableNames = new FieldHashSetWrapper();
        updateSets.stream()
                .map(UpdateSet::getColumns)
                .flatMap(Collection::stream)
                .map(m -> Optional.ofNullable(m.getTable()).map(Table::getName).orElse(table.getName()))
                .distinct()
                .forEach(f -> updateTableNames.add(f));
        columnFieldDefaultMap = columnFieldDefaultMap.entrySet().stream()
                .filter(f -> updateTableNames.contains(f.getKey().getTableAliasName()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        //2.3 没有变更设置的默认值，不处理
        if (CollectionUtil.isEmpty(columnFieldDefaultMap)) {
            return;
        }

        //3.现有的Set的字段上需要维护默认值，当前已经维护了 （1）未开启强制覆盖，则不做处理  （2）开启了强制覆盖，用策略的默认值替换现有维护的值
        for (int i = 0; i < updateSets.size(); i++) {
            UpdateSet updateSet = updateSets.get(i);
            //正常语法这里获取的columns只会有一个的，所以下面get(0)
            Column column = updateSet.getColumn(0);
            //正常情况下，多表的时候column会有所属的表名，没有的话肯定只有一张表，所以肯定是set的那张表，这种情况就把set的表名作为字段的所属表
            String tableName = Optional.ofNullable(column.getTable()).map(Table::getName).orElse(table.getName());
            String columnName = column.getColumnName();
            FieldDefault fieldDefault = CollectionUtils.getAndRemove(columnFieldDefaultMap, ColumnUniqueDto.builder().sourceColumn(columnName).tableAliasName(tableName).build());

            //3.1 左侧字段未标注@FieldDefault 或者 标注了但是未开启强制覆盖，不做处理，以原sql的为准
            if (fieldDefault == null || !fieldDefault.mandatoryOverride()) {
                continue;
            }

            //3.2 标注了@FieldDefault 并且开启了强制覆盖，此时用策略的默认值替换掉原值
            //注意：这里不能单纯的直接覆盖，如果原sql是通过#{}传参的方式预编译设置的值的话，直接替换会导致参数个数对不上，执行报错。
            //如果修改参数个数的话会涉及到在拦截器层再做一次复杂的参数对应，而且容易影响到其它拦截器
            //所以这里采用if语句给包一层， 将原先的  set xxx= ? 改为  set xxx= if(? is null,策略默认值,策略默认值) 达到不修改参数个数替换值的目的
            Expression strategyExp = ExpressionsUtil.buildFieldDefaultExp(fieldDefault.value());
            Function ifFunction = ExpressionsUtil.buildAffirmativeIf(updateSet.getValue(0), strategyExp);
            updateSet.setValues(new ExpressionList(ifFunction));
        }

        //4. 如果需要设置默认值的字段在原sql中不存在，则额外增加set的字段
        if (CollectionUtil.isNotEmpty(columnFieldDefaultMap)) {
            for (Map.Entry<ColumnUniqueDto, FieldDefault> columnEntry : columnFieldDefaultMap.entrySet()) {
                Column setColumn = ExpressionsUtil.buildColumn(columnEntry.getKey().getSourceColumn(), columnEntry.getKey().getTableAliasName());
                Expression valueExp = ExpressionsUtil.buildFieldDefaultExp(columnEntry.getValue().value());
                UpdateSet updateSet = ExpressionsUtil.buildUpdateSet(setColumn, valueExp);
                updateSets.add(updateSet);
            }
        }

        //5.处理结果赋值
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
        if (insert.toString().contains(SymbolConstant.START)) {
            log.warn("【fieldDefault】insert语句中存在 * 不支持自动设置默认值");
            return;
        }
        //3.3.获取insert的表字段顺序
        ExpressionList<Column> columns = insert.getColumns();
        if (CollectionUtils.isEmpty(columns)) {
            log.warn("【fieldDefault】insert 语句未指定表字段顺序，不支持自动设置默认值，请规范语法 原sql:{}", insert);
            return;
        }

        //4.处理 insert into table(字段...) 的字段部分   (依次解析获取每个字段上面标注的@FieldDefault信息，如果设置了默认值的字段没有在insert into(字段) 中出现，则手动添加)
        //下标是字段的顺序，从0开始，存的值是对应字段头上的@FieldDefault
        List<FieldDefault> insertFieldDefaultColumns = new ArrayList<>();
        //把存储当前表的默认值设置信息深克隆一份，避免对缓存数据的影响
        Map<String, FieldDefault> cloneFieldDefaultMap = ObjectUtil.cloneByStream(stringFieldDefaultMap);
        //4.1 将现有的字段需要维护insert的标注信息维护到上面的list中
        for (Column column : columns) {
            FieldDefault fieldDefault = CollectionUtils.getAndRemove(cloneFieldDefaultMap, column.getColumnName());
            FieldDefault currentFieldDefault = Optional.ofNullable(fieldDefault).filter(f -> FieldDefaultInstanceCache.getInstance(f.value()).whetherToHandle(SqlCommandEnum.INSERT)).orElse(null);
            insertFieldDefaultColumns.add(currentFieldDefault);
        }
        //4.2 把缺的字段补上，只保留需要设置默认值的字段
        if (CollectionUtil.isNotEmpty(cloneFieldDefaultMap)) {
            for (Map.Entry<String, FieldDefault> entry : cloneFieldDefaultMap.entrySet()) {
                if (entry.getValue() != null) {
                    Column column = ExpressionsUtil.buildColumn(entry.getKey(), null);
                    columns.add(column);
                    insertFieldDefaultColumns.add(entry.getValue());
                }
            }
        }

        //5.处理select的部分（insert into table(xxx) values (values值的部分)）
        //5.1 解析select部分
        Select select = insert.getSelect();
        FieldParseParseTableSelectVisitor fPSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
        select.accept(fPSelectVisitor);
        //5.2.处理默认值
        FieldDefaultSelectVisitor fdSelectVisitor = FieldDefaultSelectVisitor.newInstanceCurLayer(fPSelectVisitor, insertFieldDefaultColumns);
        select.accept(fdSelectVisitor);

        //6.处理ON DUPLICATE KEY UPDATE 部分
        List<UpdateSet> duplicateUpdateSets = insert.getDuplicateUpdateSets();
        if (CollectionUtils.isNotEmpty(duplicateUpdateSets)) {
            //6.1 过滤出需要维护update的默认值的字段
            Map<String, FieldDefault> updateFieldDefaultMap = stringFieldDefaultMap.entrySet().stream()
                    .filter(f -> f.getValue() != null && FieldDefaultInstanceCache.getInstance(f.getValue().value()).whetherToHandle(SqlCommandEnum.UPDATE))
                    .collect(FieldHashMapWrapper::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), (map1, map2) -> map1.putAll(map2));
            //6.2 字段之前就存在，且开启了强制覆盖，则采用if的方式，覆盖掉原值
            for (int i = 0; i < duplicateUpdateSets.size(); i++) {
                FieldDefault fieldDefault = CollectionUtils.getAndRemove(updateFieldDefaultMap, duplicateUpdateSets.get(i).getColumn(0).getColumnName());
                if (fieldDefault != null && fieldDefault.mandatoryOverride()) {
                    Expression fieldDefaultExp = ExpressionsUtil.buildFieldDefaultExp(fieldDefault.value());
                    UpdateSet updateSet = duplicateUpdateSets.get(i);
                    Function ifFun = ExpressionsUtil.buildAffirmativeIf(updateSet.getValue(0), fieldDefaultExp);
                    updateSet.setValues(new ExpressionList(ifFun));
                }
            }
            //6.3 字段原来不存在，手动添加
            if (CollectionUtil.isNotEmpty(updateFieldDefaultMap)) {
                for (Map.Entry<String, FieldDefault> entry : updateFieldDefaultMap.entrySet()) {
                    Column column = ExpressionsUtil.buildColumn(entry.getKey(), null);
                    Expression fieldDefaultExp = ExpressionsUtil.buildFieldDefaultExp(entry.getValue().value());
                    UpdateSet updateSet = ExpressionsUtil.buildUpdateSet(column, fieldDefaultExp);
                    duplicateUpdateSets.add(updateSet);
                }
            }
        }

        //7.结果集赋值
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
