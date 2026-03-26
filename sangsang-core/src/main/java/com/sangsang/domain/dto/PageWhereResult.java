package com.sangsang.domain.dto;

import com.sangsang.util.ExpressionsUtil;
import lombok.Getter;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.select.Limit;

/**
 * Oracle 分页 where 子树解析结果。
 *
 * <p>该对象同时保存两类信息：</p>
 * <p>1. 需要继续保留在 where 中的业务条件。</p>
 * <p>2. 从 where 中提炼出来的分页边界信息。</p>
 *
 * <p>之所以单独抽成 dto，是为了让 visitor 和分页转换器之间的职责更清晰：</p>
 * <p>1. visitor 负责解析表达式。</p>
 * <p>2. 转换器负责消费解析结果并改写 plainSelect。</p>
 *
 * @author liutangqi && codex && gpt-5.4
 * @date 2026/3/24 18:30
 */
@Getter
public class PageWhereResult {
    /**
     * 当前 where 子树中是否识别出了分页条件。
     */
    private final boolean hasPage;

    /**
     * 需要继续保留在 where 中的业务条件。
     */
    private final Expression retainWhere;

    /**
     * 分页下界，对应行号 >= ge。
     */
    private final Long ge;

    /**
     * 分页上界，对应行号 <= le。
     */
    private final Long le;

    /**
     * 当前 where 子树是否已经可以确定为空结果。
     */
    private final boolean emptyResult;

    private PageWhereResult(boolean hasPage, Expression retainWhere, Long ge, Long le, boolean emptyResult) {
        this.hasPage = hasPage;
        this.retainWhere = retainWhere;
        this.ge = ge;
        this.le = le;
        this.emptyResult = emptyResult;
    }

    /**
     * 当前表达式不属于分页条件。
     */
    public static PageWhereResult noPage(Expression retainWhere) {
        return new PageWhereResult(false, retainWhere, null, null, false);
    }

    /**
     * 当前表达式属于分页条件。
     */
    public static PageWhereResult page(Expression retainWhere, Long ge, Long le, boolean emptyResult) {
        return new PageWhereResult(true, retainWhere, ge, le, emptyResult);
    }

    /**
     * 将解析出的分页边界转换为 MySQL 的 limit。
     *
     * <p>如果当前子树已经被判定为空结果，则直接返回 {@code limit 0}。</p>
     */
    public Limit toLimit() {
        if (!hasPage) {
            return null;
        }
        if (emptyResult) {
            Limit limit = new Limit();
            limit.setRowCount(new LongValue(0));
            return limit;
        }
        return ExpressionsUtil.buildLimit(ge, le);
    }
}
