package com.sangsang.util.visitor;

import com.sangsang.domain.dto.ColumnTableDto;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.update.UpdateSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * pojo模式访问者工具类
 *
 * @author liutangqi
 * @date 2026/10/29 10:16
 */
public class EncryptorPojoVisitorUtil {

    /**
     * 解析updateSet中字段和占位符的对应关系
     *
     * @author liutangqi
     * @date 2026/10/28 16:00
     * @Param [updateSets, fieldParseTable]
     **/
    public static void updateSets(List<UpdateSet> updateSets,
                                       FieldParseParseTableFromItemVisitor fieldParseTable,
                                       Map<String, ColumnTableDto> placeholderColumnTableMap
    ) {
        for (UpdateSet updateSet : Optional.ofNullable(updateSets).orElse(new ArrayList<>())) {
            List<Column> columns = updateSet.getColumns();
            ExpressionList<Expression> expressions = (ExpressionList<Expression>) updateSet.getValues();
            for (int i = 0; i < columns.size(); i++) {
                JsqlparserUtil.parseWhereColumTable(fieldParseTable,
                        columns.get(i),
                        expressions.get(i),
                        placeholderColumnTableMap);
            }
        }
    }
}
