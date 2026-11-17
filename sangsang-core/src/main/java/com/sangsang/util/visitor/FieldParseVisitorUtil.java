package com.sangsang.util.visitor;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.util.CollectionUtils;
import com.sangsang.visitor.fieldparse.FieldParseParseTableFromItemVisitor;
import com.sangsang.visitor.fieldparse.FieldParseParseTableSelectVisitor;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.WithItem;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * fieldParse visitor处理中的一些共有逻辑
 *
 * @author liutangqi
 * @date 2026/9/9 9:54
 */
public class FieldParseVisitorUtil {

    /**
     * 在已有DML返回列解析作用域中注册目标表的伪表别名
     * 注意：直接复用已有的字段解析作用域，将目标表字段额外归属一份到tableAliases中
     *
     * @param baseFieldParseTable 已有的DML返回列字段解析结果
     * @param table               DML语句操作的目标表
     * @param tableAliases        DML返回列引用目标表字段时使用的伪表别名
     * @author liutangqi & gpt-5.6-sol
     * @date 2026/11/17 16:00
     **/
    public static void dmlResultTableAliases(BaseFieldParseTable baseFieldParseTable,
                                             Table table,
                                             String... tableAliases) {
        //1.目标表、解析作用域或伪表别名为空时不处理
        if (baseFieldParseTable == null || table == null || tableAliases == null) {
            return;
        }

        //2.从已有解析作用域中获取目标表字段
        String tableName = Optional.ofNullable(table.getAlias()).map(Alias::getName).orElse(table.getName());
        Map<String, List<FieldInfoDto>> fieldTableMap = baseFieldParseTable.getLayerFieldTableMap().get(baseFieldParseTable.getLayer());
        if (fieldTableMap == null) {
            return;
        }
        List<FieldInfoDto> fieldInfoDtos = fieldTableMap.get(tableName);
        if (CollectionUtils.isEmpty(fieldInfoDtos)) {
            return;
        }

        //3.将目标表字段注册到DML返回列使用的伪表别名中
        for (String tableAlias : tableAliases) {
            fieldTableMap.put(tableAlias, fieldInfoDtos);
        }
    }

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


    /**
     * 解析using语法中涉及的表结构信息
     *
     * @author liutangqi
     * @date 2026/10/29 13:39
     * @Param [usingList, fieldParseTableFromItemVisitor]
     **/
    public static void usingList(List<Table> usingList, FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor) {
        if (CollectionUtils.isEmpty(usingList)) {
            return;
        }
        for (Table usingTable : usingList) {
            usingTable.accept(fieldParseTableFromItemVisitor);
        }
    }
}
