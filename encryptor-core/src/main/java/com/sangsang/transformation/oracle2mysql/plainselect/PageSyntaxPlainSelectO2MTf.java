package com.sangsang.transformation.oracle2mysql.plainselect;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.domain.dto.PageWhereResult;
import com.sangsang.domain.dto.PlainSelectTransformationDto;
import com.sangsang.transformation.PlainSelectTransformation;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.ExpressionsUtil;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.visitor.transformation.oracle2mysql.OraclePageWhereExpressionVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.Fetch;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Offset;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Oracle 分页语法转 MySQL 分页语法。
 *
 * <p>当前类只负责分页转换的整体编排，不再自己解析 where 表达式的具体类型。</p>
 *
 * <p>处理顺序：</p>
 * <p>1. 移除 select 中仅用于分页的行号字段。</p>
 * <p>2. 使用 visitor 解析 where 中的分页条件。</p>
 * <p>3. 将分页条件转换成 limit。</p>
 * <p>4. 必要时去掉仅用于分页的外层嵌套。</p>
 * <p>5. 合并 Oracle 新版 offset/fetch 分页语法。</p>
 *
 * @author liutangqi && codex && gpt-5.4
 * @date 2026/3/24 10:35
 */
public class PageSyntaxPlainSelectO2MTf extends PlainSelectTransformation {

    @Override
    public boolean needTransformation(PlainSelectTransformationDto plainSelectTransformationDto) {
        return true;
    }

    /**
     * 单个 plainSelect 的分页转换入口。
     */
    @Override
    public PlainSelectTransformationDto doTransformation(PlainSelectTransformationDto psTfDto) {
        PlainSelect plainSelect = psTfDto.getPlainSelect();
        BaseFieldParseTable baseFieldParseTable = psTfDto.getBaseFieldParseTable();

        // 1. 去掉 select 中用于分页的行号辅助字段，例如 row_id。
        plainSelect.setSelectItems(trimRowNumberSelectItems(plainSelect.getSelectItems(), baseFieldParseTable));

        // 2. 通过 visitor 解析 where 中的分页条件，并保留普通业务条件。
        PageWhereResult pageWhereResult = resolvePageWhere(plainSelect.getWhere(), baseFieldParseTable);
        plainSelect.setWhere(pageWhereResult.getRetainWhere());
        plainSelect.setLimit(mergeLimit(plainSelect.getLimit(), pageWhereResult.toLimit()));

        // 3. 如果外层查询已经只剩分页意义，则把这层壳去掉。
        plainSelect = unwrapIfPossible(plainSelect);

        // 4. 处理 Oracle 12c 的 offset/fetch 语法。
        Limit fetchLimit = fetchToLimit(plainSelect.getOffset(), plainSelect.getFetch());
        plainSelect.setOffset(null);
        plainSelect.setFetch(null);
        plainSelect.setLimit(mergeLimit(plainSelect.getLimit(), fetchLimit));

        psTfDto.setPlainSelect(plainSelect);
        return psTfDto;
    }

    /**
     * 移除 selectItems 中仅用于分页的行号字段。
     */
    private List<SelectItem<?>> trimRowNumberSelectItems(List<SelectItem<?>> selectItems, BaseFieldParseTable baseFieldParseTable) {
        if (CollectionUtils.isEmpty(selectItems)) {
            return selectItems;
        }

        List<SelectItem<?>> resSelectItems = new ArrayList<>();
        for (SelectItem<?> selectItem : selectItems) {
            if (isRowNumberSelectItem(selectItem, baseFieldParseTable)) {
                continue;
            }
            resSelectItems.add(selectItem);
        }
        return resSelectItems;
    }

    /**
     * 判断当前字段是否属于分页辅助字段。
     */
    private boolean isRowNumberSelectItem(SelectItem<?> selectItem, BaseFieldParseTable baseFieldParseTable) {
        Expression expression = selectItem.getExpression();
        if (!(expression instanceof Column)) {
            return false;
        }

        Column column = (Column) expression;
        if (JsqlparserUtil.rowNumber(column)) {
            return true;
        }

        ColumnTableDto columnTableDto = JsqlparserUtil.parseColumn(column, selectItem.getAlias(), baseFieldParseTable);
        return columnTableDto.isRowNumber();
    }

    /**
     * 使用 visitor 解析 where 中的分页条件。
     */
    private PageWhereResult resolvePageWhere(Expression where, BaseFieldParseTable baseFieldParseTable) {
        if (where == null) {
            return PageWhereResult.noPage(null);
        }
        OraclePageWhereExpressionVisitor visitor = OraclePageWhereExpressionVisitor.newInstanceCurLayer(baseFieldParseTable);
        where.accept(visitor);
        return visitor.getProcess() == null ? PageWhereResult.noPage(where) : visitor.getProcess();
    }

    /**
     * 当外层查询仅仅是为了分页而套的一层壳时，去掉这一层。
     */
    private PlainSelect unwrapIfPossible(PlainSelect plainSelect) {
        if (!canUnwrap(plainSelect)) {
            return plainSelect;
        }

        Limit upstreamLimit = plainSelect.getLimit();
        ParenthesedSelect parenthesedSelect = (ParenthesedSelect) plainSelect.getFromItem();
        PlainSelect innerPlainSelect = (PlainSelect) parenthesedSelect.getSelect();
        innerPlainSelect.setLimit(mergeLimit(innerPlainSelect.getLimit(), upstreamLimit));
        return innerPlainSelect;
    }

    /**
     * 判断当前外层查询是否可以安全去壳。
     */
    private boolean canUnwrap(PlainSelect plainSelect) {
        List<SelectItem<?>> selectItems = plainSelect.getSelectItems();
        if (CollectionUtils.isEmpty(selectItems) || selectItems.size() != 1) {
            return false;
        }
        if (!(selectItems.get(0).getExpression() instanceof AllColumns)
                && !(selectItems.get(0).getExpression() instanceof AllTableColumns)) {
            return false;
        }
        if (plainSelect.getWhere() != null) {
            return false;
        }

        List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
        if (CollectionUtils.isNotEmpty(orderByElements)) {
            return false;
        }

        FromItem fromItem = plainSelect.getFromItem();
        return fromItem instanceof ParenthesedSelect && ((ParenthesedSelect) fromItem).getSelect() instanceof PlainSelect;
    }

    /**
     * 把 Oracle offset/fetch 转成 MySQL limit。
     */
    private Limit fetchToLimit(Offset offset, Fetch fetch) {
        return ExpressionsUtil.fetch2Limit(offset, fetch);
    }

    /**
     * 合并两个 limit。
     *
     * <p>只要任意一边已经明确为空结果，则最终仍然为空结果。</p>
     */
    private Limit mergeLimit(Limit firstLimit, Limit secondLimit) {
        if (isEmptyLimit(firstLimit) || isEmptyLimit(secondLimit)) {
            return buildEmptyLimit();
        }
        return ExpressionsUtil.mergeLimit(firstLimit, secondLimit);
    }

    /**
     * 判断当前 limit 是否明确表示空结果。
     */
    private boolean isEmptyLimit(Limit limit) {
        if (limit == null || !(limit.getRowCount() instanceof LongValue)) {
            return false;
        }
        return ((LongValue) limit.getRowCount()).getValue() == 0L;
    }

    /**
     * 构造一个 limit 0。
     */
    private Limit buildEmptyLimit() {
        Limit limit = new Limit();
        limit.setRowCount(new LongValue(0));
        return limit;
    }
}
