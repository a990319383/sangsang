package com.sangsang.transformation.oracle2mysql.plainselect;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.domain.dto.PlainSelectTransformationDto;
import com.sangsang.transformation.PlainSelectTransformation;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/*
 * oracle切换到mysql的话，建议直接使用mysql8.0以上的版本，天然支持窗口函数
 * 如果使用mysql5.7的话，利用变量的方式改写窗口函数
 * -注意点1：同一个sql的select 字段可能存在多个窗口函数
 * -注意点2：窗口函数 ROW_NUMBER()中可能存在PARTITION BY 可能不存在
 * -注意点3：窗口函数 ROW_NUMBER()中可能存在ORDER BY 可能不存在
 * -注意点4：当前sql查询的字段中有窗口函数，此sql本身还自带得有order by的条件
 *
 * @author gemini
 * @date 2026/1/15 18:00
 */
public class RowNumberPlainSelectO2MTf extends PlainSelectTransformation {

    /**
     * 当前mysql的版本是8.0以上的话，就不用进行窗口函数的转换
     *
     * @author liutangqi
     * @date 2026/1/16 14:34
     * @Param [plainSelectTransformationDto]
     **/
    @Override
    public boolean needTransformation(PlainSelectTransformationDto plainSelectTransformationDto) {
        //拿不到当前的主版本号信息 || 当前主版本< 8 ，则表示当前mysql不兼容窗口函数，需要进行转换
        return TableCache.getDataSourceConfig().getDatabaseMajorVersion() == null
                || TableCache.getDataSourceConfig().getDatabaseMajorVersion() < NumberConstant.EIGHT;
    }

    @Override
    public PlainSelectTransformationDto doTransformation(PlainSelectTransformationDto psTfDto) {
        PlainSelect currentSelect = psTfDto.getPlainSelect();

        // 1. 提取 SelectItems 中的窗口函数
        List<SelectItem> windowItems = currentSelect.getSelectItems().stream()
                .filter(item -> item.getExpression() instanceof AnalyticExpression)
                .collect(Collectors.toList());
        if (windowItems.isEmpty()) {
            return psTfDto;
        }

        // 2. 提取并暂存原 SQL 的 ORDER BY (注意点4：原 SQL 可能带 ORDER BY)
        List<OrderByElement> originalOrderBy = currentSelect.getOrderByElements();
        currentSelect.setOrderByElements(null);

        // 3. 将原 SQL 基础部分（去除窗口函数列）作为初始数据源
        // 这里建议克隆一份，防止污染原始对象
        PlainSelect baseSelect = currentSelect;
        List<SelectItem<?>> nonWindowItems = baseSelect.getSelectItems().stream()
                .filter(item -> !(item.getExpression() instanceof AnalyticExpression)).collect(Collectors.toList());
        baseSelect.setSelectItems(nonWindowItems);

        // 4. 循环处理每一个窗口函数（注意点1：处理多个窗口函数）
        PlainSelect currentRoot = baseSelect;
        int step = 0;
        for (SelectItem windowItem : windowItems) {
            AnalyticExpression ae = (AnalyticExpression) windowItem.getExpression();
            String alias = windowItem.getAlias() != null ? windowItem.getAlias().getName() : "rn_" + (++step);

            // 递归包装一层
            currentRoot = wrapWithMysqlVariableLayer(currentRoot, ae, alias);
        }

        // 5. 如果原 SQL 有排序，在最外层再套一层执行最终排序
        if (originalOrderBy != null && !originalOrderBy.isEmpty()) {
            PlainSelect finalWrapper = new PlainSelect();
            ParenthesedSelect sub = new ParenthesedSelect();
            sub.setSelect(currentRoot);
            sub.setAlias(new Alias("final_res"));
            finalWrapper.setFromItem(sub);
            finalWrapper.addSelectItems(new AllColumns());
            finalWrapper.setOrderByElements(originalOrderBy);
            currentRoot = finalWrapper;
        }

        psTfDto.setPlainSelect(currentRoot);
        return psTfDto;
    }

    private PlainSelect wrapWithMysqlVariableLayer(PlainSelect innerSelectBody, AnalyticExpression ae, String colAlias) {
        PlainSelect outer = new PlainSelect();

        // --- 步骤 A: 处理内部排序 (注意点2 & 3) ---
        // 变量模拟必须要求内层数据按 Partition 和 Order 排序
        List<OrderByElement> internalSorts = new ArrayList<>();
        if (ae.getPartitionExpressionList() != null) {
            internalSorts.addAll(ae.getPartitionExpressionList());
        }
        if (ae.getOrderByElements() != null) {
            internalSorts.addAll(ae.getOrderByElements());
        }

        if (innerSelectBody instanceof PlainSelect) {
            ((PlainSelect) innerSelectBody).setOrderByElements(internalSorts);
        }

        // --- 步骤 B: 构造子查询数据源 ---
        ParenthesedSelect subSource = new ParenthesedSelect();
        subSource.setSelect(innerSelectBody);
        subSource.setAlias(new Alias("t_" + colAlias));
        outer.setFromItem(subSource);

        // --- 步骤 C: 注入变量计算逻辑 ---
        outer.addSelectItems(new AllColumns()); // 继承之前所有列

        // 构造: @rn := IF(@p_key = partition_col, @rn + 1, 1)
        String partitionKey = (ae.getPartitionExpressionList() != null && !ae.getPartitionExpressionList().isEmpty()) ? ae.getPartitionExpressionList().get(0).toString() : "'single_partition'"; // 如果没有 Partition By，则视为全局一个分区

        // 逻辑：计算行号
        String rnLogic = String.format("(@rn_%s := IF(@p_%s = %s, @rn_%s + 1, 1))", colAlias, colAlias, partitionKey, colAlias);
        SelectItem rnSelectItem = new SelectItem(new Column(rnLogic));
        rnSelectItem.setAlias(new Alias(colAlias));

        // 逻辑：更新变量
        String upLogic = String.format("(@p_%s := %s)", colAlias, partitionKey);
        SelectItem upSelectItem = new SelectItem(new Column(upLogic));

        outer.addSelectItems(rnSelectItem, upSelectItem);

        // --- 步骤 D: 注入变量初始化 (Join) ---
        Join varInitJoin = new Join();
        varInitJoin.setSimple(true); // 使用逗号连接

        PlainSelect initSelect = new PlainSelect();
        initSelect.addSelectItems(new SelectItem(new Column("@rn_" + colAlias + " := 0")));
        initSelect.addSelectItems(new SelectItem(new Column("@p_" + colAlias + " := ''")));

        ParenthesedSelect varInitSub = new ParenthesedSelect();
        varInitSub.setSelect(initSelect);
        varInitSub.setAlias(new Alias("v_" + colAlias));

        varInitJoin.setRightItem(varInitSub);
        outer.addJoins(varInitJoin);

        return outer;
    }
}
