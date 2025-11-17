package com.sangsang.visitor.fieldparse;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.constants.FieldConstant;
import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.wrapper.FieldHashSetWrapper;
import com.sangsang.util.JsqlparserUtil;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 解析select 语句中每一层的sql中用到的表和该表的全部字段
 *
 * @author liutangqi
 * @date 2024/3/4 10:11
 */
public class FieldParseParseTableFromItemVisitor extends BaseFieldParseTable implements FromItemVisitor {

    /**
     * 获取第一层对象
     *
     * @author liutangqi
     * @date 2025/3/4 16:47
     * @Param [baseFieldParseTable]
     **/
    public static FieldParseParseTableFromItemVisitor newInstanceFirstLayer() {
        return new FieldParseParseTableFromItemVisitor(
                NumberConstant.ONE,
                null,
                null
        );
    }

    /**
     * 获取当前层的解析对象
     *
     * @author liutangqi
     * @date 2025/3/4 17:17
     * @Param [baseFieldParseTable]
     **/
    public static FieldParseParseTableFromItemVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new FieldParseParseTableFromItemVisitor(baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap());
    }

    private FieldParseParseTableFromItemVisitor(int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    @Override
    public void visit(Table table) {
        //1.当前表表名信息
        String tableName = table.getName();
        String aliasTable = Optional.ofNullable(table.getAlias()).map(Alias::getName).orElse(tableName);

        //2.获取当前表的全部字段信息
        Set<FieldInfoDto> fieldInfoSet = Optional.ofNullable(TableCache.getTableFieldMap().get(tableName))
                .orElse(new FieldHashSetWrapper())
                .stream()
                .map(m -> FieldInfoDto.builder().columnName(m).sourceTableName(tableName).fromSourceTable(true).sourceColumn(m).build())
                .collect(Collectors.toSet());

        //3.将这些字段信息维护到 layerFieldTableMap 中
        JsqlparserUtil.putFieldInfo(this.getLayerFieldTableMap(), this.getLayer(), aliasTable, fieldInfoSet);
    }


    /**
     * 子查询当前层的表的全部字段，就是下一层的select的全部字段
     *
     * @author liutangqi
     * @date 2024/3/5 15:53
     * @Param [subSelect]
     **/
    @Override
    public void visit(ParenthesedSelect subSelect) {
//        int layer = this.getLayer(); 注意：这里不能使用这样写，必须用this.getLayer() 存在类似递归的操作，这里的变量layer可能是上一层的，而另外两个Map是所有层级共享的
        //0.子查询的别名，作为当前层字段的表名 某些数据库子查询不一定需要别名，这里就用FieldConstant.VIRTUAL_TABLE_ALIAS + 层数 作为别名
        String aliasTable = Optional.ofNullable(subSelect.getAlias()).map(Alias::getName).orElse(FieldConstant.VIRTUAL_TABLE_ALIAS + this.getLayer());

        //1.解析子查询下一层，层数 + 1
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceNextLayer(this);
        subSelect.getSelect().accept(fieldParseTableSelectVisitor);

        //2.解析这一层涉及到的表的全部字段，子查询的时，本层的表的全部字段就是下一层的全部select的字段，本层的表名就是别名
        Map<String, Set<FieldInfoDto>> selectTableFieldMap = this.getLayerSelectTableFieldMap().getOrDefault(String.valueOf(this.getLayer() + 1), new HashMap<>());
        //本层的字段都是来源于嵌套查询的结果集，不是真实表，所以将 fromSourceTable设置为false
        Set<FieldInfoDto> fieldInfoSet = selectTableFieldMap
                .values()
                .stream()
                .flatMap(Collection::stream)
                .map(m -> FieldInfoDto.builder()
                        .fromSourceTable(false)
                        .columnName(m.getColumnName())
                        .sourceColumn(m.getSourceColumn())
                        .sourceTableName(m.getSourceTableName())
                        .build())
                .collect(Collectors.toSet());

        //3. 将当前层的全部字段维护进 layerFieldTableMap 中
        JsqlparserUtil.putFieldInfo(this.getLayerFieldTableMap(), this.getLayer(), aliasTable, fieldInfoSet);
    }


    @Override
    public void visit(LateralSubSelect lateralSubSelect) {
        System.out.println("当前语法未适配");
    }

    /**
     * 某些语法构建出一张虚拟表时会走这个
     * 此场景一般不会有加解密的需求
     * 栗子：ck 中的 numbers()函数就会走这里
     * SELECT
     * toDate(addDays(fromUnixTimestamp64Milli(#{startTime}, 'UTC'),number)) as statTime
     * from numbers(1,dateDiff('day', fromUnixTimestamp64Milli(#{startTime}, 'UTC') , fromUnixTimestamp64Milli( #{endTime}, 'UTC')))
     *
     * @author liutangqi
     * @date 2024/9/24 9:57
     * @Param [tableFunction]
     **/
    @Override
    public void visit(TableFunction tableFunction) {
//        System.out.println("当前语法未适配");
    }

    @Override
    public void visit(ParenthesedFromItem aThis) {

    }
}
