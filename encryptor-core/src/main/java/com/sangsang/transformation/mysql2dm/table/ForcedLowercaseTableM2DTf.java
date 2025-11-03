package com.sangsang.transformation.mysql2dm.table;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.constants.SymbolConstant;
import com.sangsang.transformation.TableTransformation;
import net.sf.jsqlparser.schema.Table;

/**
 * 开启了强制将字段和表名双引号引起来的话，将表名给引起来
 *
 * @author liutangqi
 * @date 2025/10/30 10:33
 */
public class ForcedLowercaseTableM2DTf extends TableTransformation {
    @Override
    public boolean needTransformation(Table table) {
        //配置开启强制将字段和表名双引号引 && 不包含 ` && 不包含 "
        return TableCache.getCurConfig().getTransformation().isForcedLowercase()
                && !table.getName().contains(SymbolConstant.FLOAT)
                && !table.getName().contains(SymbolConstant.DOUBLE_QUOTES);
    }

    @Override
    public Table doTransformation(Table table) {
        String tableName = table.getName();
        table.setName(SymbolConstant.DOUBLE_QUOTES + tableName.toLowerCase() + SymbolConstant.DOUBLE_QUOTES);
        return table;
    }
}
