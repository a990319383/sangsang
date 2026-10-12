package com.sangsang.util.visitor;

import cn.hutool.core.collection.CollectionUtil;
import com.sangsang.domain.annos.fielddefault.FieldDefault;
import com.sangsang.domain.dto.ColumnUniqueDto;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.enums.SqlCommandEnum;
import com.sangsang.domain.wrapper.FieldHashSetWrapper;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.ExpressionsUtil;
import com.sangsang.util.JsqlparserUtil;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
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
            defaultTableName.set(fieldInfoDtoMap.keySet().iterator().next());
        }

        //4.整理出当前发生了set的表
        Set<String> updateTableNames = new FieldHashSetWrapper();
        updateSets.stream()
                .map(UpdateSet::getColumns)
                .flatMap(Collection::stream)
                .map(m -> Optional.ofNullable(m.getTable()).map(Table::getName).orElse(defaultTableName.get()))
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
                String tableName = Optional.ofNullable(column.getTable()).map(Table::getName).orElse(defaultTableName.get());
                String columnName = column.getColumnName();
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

}
