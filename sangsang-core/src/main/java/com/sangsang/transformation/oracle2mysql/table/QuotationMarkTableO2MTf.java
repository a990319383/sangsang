package com.sangsang.transformation.oracle2mysql.table;

import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.transformation.TableTransformation;
import net.sf.jsqlparser.schema.Table;

/**
 * 表名包含双引号 的转换为 `
 * 栗子："tb_user"  ===> `tb_user`
 *
 * @author liutangqi
 * @date 2025/5/29 17:45
 */
public class QuotationMarkTableO2MTf extends TableTransformation {

    @Override
    public boolean needTransformation(Table table) {
        //当前表名包含 `
        return table.getName().contains(SymbolConstant.DOUBLE_QUOTES);
    }

    @Override
    public Table doTransformation(Table table) {
        String tableName = table.getName();
        String trimTableName = tableName.replaceAll(SymbolConstant.DOUBLE_QUOTES, SymbolConstant.FLOAT);
        table.setName(trimTableName);
        return table;
    }
}
