package com.sangsang.domain.dto;


import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.util.StringUtils;
import lombok.*;

import java.util.Locale;
import java.util.Objects;


/**
 * 标识字段唯一的信息
 *
 * @author liutangqi
 * @date 2025/7/18 9:24
 **/
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ColumnUniqueDto {
    /**
     * 字段所属的表的别名(from 后面接的表的别名)
     * 有别名是别名，没别名就是真实表名
     */
    private String tableAliasName;
    /**
     * 字段字段所属的真实表名
     */
    private String sourceTableName;
    /**
     * 字段名
     */
    private String sourceColumn;

    /**
     * sourceColumn + tableAliasName相同，就判定相同
     * 关于相同的定义：会根据当前项目配置，判断是否进行大小写敏感，并不考虑关键字，判断两者是否相等
     *
     * @author liutangqi
     * @date 2026/10/10 17:07
     * @Param [obj]
     **/
    @Override
    public boolean equals(Object obj) {
        //判断整个对象
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        // sourceColumn 字段名不同，那肯定不同
        ColumnUniqueDto columnUniqueObj = (ColumnUniqueDto) obj;
        if (!StringUtils.fieldEquals(this.sourceColumn, columnUniqueObj.sourceColumn)) {
            return false;
        }

        // sourceColumn 字段名相同，所属表别名相同，就是同一个字段
        return StringUtils.fieldEquals(this.tableAliasName, columnUniqueObj.tableAliasName);
    }

    @Override
    public int hashCode() {
        //1.获取表别名和字段名
        String tableAliasKey = this.tableAliasName;
        String sourceColumnKey = this.sourceColumn;

        //2.如果当前大小写不敏感，则统一转换为小写
        if (!TableCache.getCurConfig().isCaseSensitive()) {
            tableAliasKey = tableAliasKey.toLowerCase(Locale.ROOT);
            sourceColumnKey = sourceColumnKey.toLowerCase(Locale.ROOT);
        }

        //3.去除关键字标识符
        for (String identifierQuote : TableCache.getCurConfig().getIdentifierQuote()) {
            tableAliasKey = StringUtils.trimSymbol(tableAliasKey, identifierQuote);
            sourceColumnKey = StringUtils.trimSymbol(sourceColumnKey, identifierQuote);
        }

        //4.使用表别名和字段名计算hashCode
        return Objects.hash(tableAliasKey, sourceColumnKey);
    }
}
