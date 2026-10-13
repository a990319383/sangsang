package com.sangsang.util.visitor;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.sangsang.cache.fielddefault.FieldDefaultInstanceCache;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.annos.fielddefault.FieldDefault;
import com.sangsang.domain.dto.ColumnUniqueDto;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.enums.SqlCommandEnum;
import com.sangsang.domain.exception.FieldException;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.domain.wrapper.FieldHashSetWrapper;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.ExpressionsUtil;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * FieldDefault
 *
 * @author liutangqi
 * @date 2026/10/10 18:23
 */
@Slf4j
public class FieldDefaultVisitorUtil {

    /**
     * 字段设置默认值功能处理updateSet语句
     *
     * @author liutangqi
     * @date 2026/10/10 18:26
     * @Param [updateSets, fieldInfoDtoMap ：当前层拥有的字段信息，sqlCommandEnum：当前属于数据新增还是数据修改的语法]
     **/
    public static void updateSets(List<UpdateSet> updateSets, Map<String, List<FieldInfoDto>> fieldInfoDtoMap, SqlCommandEnum sqlCommandEnum) {
        //1. 没有updateSet语句，不处理
        if (CollectionUtils.isEmpty(updateSets)) {
            return;
        }

        //2.获取当前sql涉及到的表字段需要设置默认值的字段信息
        Map<ColumnUniqueDto, FieldDefault> columnFieldDefaultMap = JsqlparserUtil.filterFieldDefault(fieldInfoDtoMap, sqlCommandEnum);
        if (CollectionUtils.isEmpty(columnFieldDefaultMap)) {
            return;
        }

        //3.如果当前层只有一张表的话，将这张表的表名作为字段所属的默认表名，update 的set语法如果没有指定别名时，只有字段名，此时我们就把这个默认表名作为字段的表名去匹配默认值策略
        AtomicReference<String> defaultTableName = new AtomicReference(null);
        if (fieldInfoDtoMap.size() == 1) {
            //只有一张表，这里取当前sql作用域中的表别名作为默认表名
            defaultTableName.set(fieldInfoDtoMap.keySet().iterator().next());
        }

        //4.整理出当前发生了set的表 （有所属table的话，优先取所属table的表别名，没有的话，取默认表名）
        Set<String> updateTableNames = new FieldHashSetWrapper();
        updateSets.stream()
                .map(UpdateSet::getColumns)
                .flatMap(Collection::stream)
                .map(m -> getUpdateColumnTableName(m, fieldInfoDtoMap, defaultTableName.get()))
                .filter(StringUtils::isNotBlank)
                .distinct()
                .forEach(f -> updateTableNames.add(f));

        //5.过滤出涉及FieldDefault的并且发生了set的表
        columnFieldDefaultMap = columnFieldDefaultMap.entrySet().stream()
                .filter(f -> updateTableNames.contains(f.getKey().getTableAliasName()) || updateTableNames.contains(f.getKey().getSourceTableName()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        //6.如果当前set的字段不涉及 FieldDefault 则不做任何处理
        if (CollectionUtil.isEmpty(columnFieldDefaultMap)) {
            return;
        }

        //7.现有的Set的字段上需要维护默认值，当前已经维护了不为null值的 （1）未开启强制覆盖，则不做处理  （2）开启了强制覆盖，用策略的默认值替换现有维护的值
        for (int i = 0; i < updateSets.size(); i++) {
            UpdateSet updateSet = updateSets.get(i);
            //columns当出现语法u5,u6的时候，就会是一个长度大于1的数组
            ExpressionList<Column> columns = updateSet.getColumns();

            for (int j = 0; j < columns.size(); j++) {
                Column column = columns.get(j);
                //获取这个字段所属表别名
                String tableName = getUpdateColumnTableName(column, fieldInfoDtoMap, defaultTableName.get());
                String columnName = column.getColumnName();
                //注意：这里的tableName统一为当前sql作用域中的表别名
                FieldDefault fieldDefault = CollectionUtils.getAndRemove(columnFieldDefaultMap, ColumnUniqueDto.builder().sourceColumn(columnName).tableAliasName(tableName).build());
                //7.1 左侧字段未标注@FieldDefault不做处理
                if (fieldDefault == null) {
                    continue;
                }
                //7.2 标注了@FieldDefault，这里先获取策略的默认值
                Expression strategyExp = ExpressionsUtil.buildFieldDefaultExp(fieldDefault.value());
                //7.3 如果这里是子查询的话，数组长度只能是1，并且类型是子查询（子查询需要将子查询的查询项进行默认值替换） todo-ltq 这里后续切换为走visitor
                ExpressionList updateSetValues = updateSet.getValues();
                if (updateSetValues.size() == 1 && updateSetValues.get(0) instanceof ParenthesedSelect) {
                    //子查询如果未开启强制覆盖的话，不会使用默认值去替换子查询的结果值
                    if (!fieldDefault.mandatoryOverride()) {
                        continue;
                    }
                    ParenthesedSelect parenthesedSelect = (ParenthesedSelect) updateSetValues.get(0);
                    Select select = parenthesedSelect.getSelect();
                    List<SelectItem<?>> selectItems = select.getPlainSelect().getSelectItems();
                    //子查询的查询项只处理和左侧Column对应的查询项
                    SelectItem<?> selectItem = selectItems.get(j);
                    //为了防止#{}参数发生变化，这里将策略的默认值和原值使用case when语法包装一下，使得结果一定是策略默认值
                    Expression caseWhenExp = ExpressionsUtil.buildAffirmative(selectItem.getExpression(), strategyExp);
                    selectItems.set(j, new SelectItem(caseWhenExp));
                }
                //7.4 不是子查询，是普通字段的话，就根据左侧Columns的情况，进行默认值替换处理
                else {
                    //非子查询，当前sql设置的值如果不为null并且未开启强制覆盖的话，不会使用默认值去替换sql原来设置的值
                    if (!(updateSet.getValue(j) instanceof NullValue) && !fieldDefault.mandatoryOverride()) {
                        continue;
                    }
                    //为了防止#{}参数发生变化，这里将策略的默认值和原值使用case when语法包装一下，使得结果一定是策略默认值
                    Expression caseWhenExp = ExpressionsUtil.buildAffirmative(updateSet.getValue(j), strategyExp);
                    updateSetValues.set(j, caseWhenExp);
                }
            }
        }

        //8. 如果需要设置默认值的字段在原sql中不存在，则额外增加set的字段
        if (CollectionUtil.isNotEmpty(columnFieldDefaultMap)) {
            for (Map.Entry<ColumnUniqueDto, FieldDefault> columnEntry : columnFieldDefaultMap.entrySet()) {
                //当前只有一张表的时候（也就是defaultTableName默认表名不为null），增加的set字段不设置所属表名  （PS:在某些数据库比如oracle中，这种场景以表名作为别名.字段名会解析错误）
                String tableName = defaultTableName.get() != null ? null : columnEntry.getKey().getTableAliasName();
                Column setColumn = ExpressionsUtil.buildColumn(columnEntry.getKey().getSourceColumn(), tableName);
                Expression valueExp = ExpressionsUtil.buildFieldDefaultExp(columnEntry.getValue().value());
                UpdateSet updateSet = ExpressionsUtil.buildUpdateSet(setColumn, valueExp);
                updateSets.add(updateSet);
            }
        }
    }


    /**
     * 获取update set字段所属的表别名
     *
     * @author liutangqi
     * @date 2026/10/13 15:50
     * @Param [column, fieldInfoDtoMap, defaultTableName]
     **/
    private static String getUpdateColumnTableName(Column column,
                                                   Map<String, List<FieldInfoDto>> fieldInfoDtoMap,
                                                   String defaultTableName) {
        //1.字段指定了所属表，直接使用字段所属的表别名
        String tableName = Optional.ofNullable(column.getTable())
                .map(Table::getName)
                .orElse(null);
        if (StringUtils.isNotBlank(tableName)) {
            return tableName;
        }

        //2.当前只有一张表，使用默认表别名
        if (StringUtils.isNotBlank(defaultTableName)) {
            return defaultTableName;
        }

        //3.多表update中字段未指定所属表，根据字段名匹配这一层中字段名一致的
        List<String> tableNames = fieldInfoDtoMap.entrySet()
                .stream()
                .filter(f -> f.getValue()
                        .stream()
                        .anyMatch(fieldInfoDto -> StringUtils.fieldEquals(column.getColumnName(), fieldInfoDto.getColumnName())
                                || StringUtils.fieldEquals(column.getColumnName(), fieldInfoDto.getSourceColumn())))
                .map(Map.Entry::getKey)
                .distinct()
                .collect(Collectors.toList());

        //4.只匹配到一张表，返回该表别名
        if (tableNames.size() == 1) {
            return tableNames.get(0);
        }

        //5.未匹配到表或匹配到多张表，无法确认字段所属表
        log.error("【sangsang】<field-default>多表update中字段未指定所属表，无法确认字段所属表，无法处理此字段。字段名:{}，匹配表:{}", column.getColumnName(), tableNames);
        throw new FieldException("【sangsang】<field-default>多表update中字段未指定所属表，无法确认字段归属表");
    }


    /**
     * 依次解析获取每个字段上面标注的@FieldDefault信息，如果设置了默认值的字段没有在insert into(字段) 中出现，则手动添加
     * 注意：标准的insert语法才做处理
     * 备注：只处理处理insert into table(字段) 语法中的字段部分, insert into set这种非标准的sql的话，insert.getSelect()是null
     *
     * @author liutangqi
     * @date 2026/10/13 13:35
     * @Param [insert, stringFieldDefaultMap :此表字段对应的@FieldDefault 的信息]
     **/
    public static List<FieldDefault> insertFieldDefaultColumns(Insert insert, Map<String, FieldDefault> stringFieldDefaultMap) {
        List<FieldDefault> insertFieldDefaultColumns = new ArrayList<>();
        //1. 非标准的insert 语法，比如 insert into set 语法，不做处理，直接返回空结果集
        if (insert.getSelect() == null) {
            return insertFieldDefaultColumns;
        }

        //2. 获取insert的表字段顺序，表结构信息
        ExpressionList<Column> columns = insert.getColumns();
        Table table = insert.getTable();

        //3. 如果insert into 表名(字段...) 这种语法，且未开启自动获取表结构的话，我们是没办法获取到正确的字段顺序的，给用户警告日志，让用户自行调整
        if (!TableCache.getCurConfig().isAutoFill() && CollectionUtils.isEmpty(columns)) {
            log.warn("【sangsang】insert 语句未指定表字段顺序，且当前未开启自动读取表结构，无法获取表字段顺序。此情况无法设置默认值！！！请规范语法或开启 sangsang.autoFill=true  原sql:{}", insert.toString());
            return insertFieldDefaultColumns;
        }

        //4. 如果insert into 表名(字段...) 这种语法，但是开启自动获取表结构，这个时候我们进行手动补全
        if (TableCache.getCurConfig().isAutoFill() && CollectionUtils.isEmpty(columns)) {
            List<String> fieldLinkedList = TableCache.getTableFieldMap().get(table.getName());
            columns = new ExpressionList(fieldLinkedList.stream().map(m -> ExpressionsUtil.buildColumn(m, table.getName())).collect(Collectors.toList()));
            insert.setColumns(columns);
        }

        //5.把存储当前表的默认值设置信息深克隆一份，避免对缓存数据的影响
        Map<String, FieldDefault> cloneFieldDefaultMap = ObjectUtil.cloneByStream(stringFieldDefaultMap);

        //6. 将现有的字段需要维护insert的标注信息维护到上面的list中
        for (Column column : columns) {
            FieldDefault fieldDefault = CollectionUtils.getAndRemove(cloneFieldDefaultMap, column.getColumnName());
            FieldDefault currentFieldDefault = Optional.ofNullable(fieldDefault).filter(f -> FieldDefaultInstanceCache.getInstance(f.value()).whetherToHandle(SqlCommandEnum.INSERT)).orElse(null);
            insertFieldDefaultColumns.add(currentFieldDefault);
        }

        //7. 把缺的字段补上，只保留需要设置默认值的字段 （注意：这里只补insert时需要维护的字段，所以这里加了层过滤）
        cloneFieldDefaultMap = cloneFieldDefaultMap
                .entrySet()
                .stream()
                .filter(f -> FieldDefaultInstanceCache.getInstance(f.getValue().value()).whetherToHandle(SqlCommandEnum.INSERT))
                .collect(FieldHashMapWrapper::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), (map1, map2) -> map1.putAll(map2));
        if (CollectionUtil.isNotEmpty(cloneFieldDefaultMap)) {
            for (Map.Entry<String, FieldDefault> entry : cloneFieldDefaultMap.entrySet()) {
                if (entry.getValue() != null) {
                    Column column = ExpressionsUtil.buildColumn(entry.getKey(), null);
                    columns.add(column);
                    insertFieldDefaultColumns.add(entry.getValue());
                }
            }
        }
        return insertFieldDefaultColumns;
    }
}
