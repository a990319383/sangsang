package com.sangsang.transformation.mysql2dm.table;

import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.transformation.TableTransformation;
import net.sf.jsqlparser.schema.Table;

/**
 * 表名包含 ` 的转换为双引号
 * 栗子：`tb_user` ===> "tb_user"
 *
 * @author liutangqi
 * @date 2025/5/29 17:45
 */
public class QuotationMarkTableM2DTf extends TableTransformation {

    @Override
    public boolean needTransformation(Table table) {
        //当前表名包含 `
        return table.getName().contains(SymbolConstant.FLOAT);
    }

    @Override
    public Table doTransformation(Table table) {
        String tableName = table.getName();
        String trimTableName = tableName.replaceAll(SymbolConstant.FLOAT, SymbolConstant.DOUBLE_QUOTES);
        table.setName(trimTableName);
        return table;
    }
}
