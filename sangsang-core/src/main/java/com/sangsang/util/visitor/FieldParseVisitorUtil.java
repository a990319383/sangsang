package com.sangsang.util.visitor;

import com.sangsang.util.CollectionUtils;
import com.sangsang.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.WithItem;

import java.util.List;

/**
 * fieldParse visitor处理中的一些共有逻辑
 *
 * @author liutangqi
 * @date 2026/9/9 9:54
 */
public class FieldParseVisitorUtil {

    /**
     * 解析CTE语法 栗如： WITH x AS (SELECT * FROM tb_user)
     *
     * @author liutangqi
     * @date 2026/9/9 10:00
     * @Param [withItems, fieldParseParseTableSelectVisitor]
     **/
    public static void cte(List<WithItem> withItems, FieldParseParseTableSelectVisitor fieldParseParseTableSelectVisitor) {
        if (CollectionUtils.isEmpty(withItems)) {
            return;
        }
        for (WithItem withItem : withItems) {
            withItem.accept(fieldParseParseTableSelectVisitor);
        }
    }

    /**
     * 解析join语法中涉及的表结构信息
     * 备注：on后的内容解析来没用，所以没有处理
     *
     * @author liutangqi
     * @date 2026/9/9 15:13
     * @Param [joins, fieldParseParseTableFromItemVisitor]
     **/
    public static void joins(List<Join> joins, FieldParseParseTableFromItemVisitor fieldParseParseTableFromItemVisitor) {
        if (CollectionUtils.isEmpty(joins)) {
            return;
        }
        for (Join join : joins) {
            if (join.getRightItem() == null) {
                continue;
            }
            join.getRightItem().accept(fieldParseParseTableFromItemVisitor);
        }

    }
}
