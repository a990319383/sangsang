package com.sangsang.visitor.fieldparse;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.constants.FieldConstant;
import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import com.sangsang.domain.wrapper.FieldLinkedListWarpper;
import com.sangsang.domain.wrapper.LayerHashMapWrapper;
import com.sangsang.util.CollectionUtils;
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

    private FieldParseParseTableFromItemVisitor(int layer, Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    @Override
    public void visit(Table table) {
        //1.当前表表名信息
        String tableName = table.getName();
        String aliasTable = Optional.ofNullable(table.getAlias()).map(Alias::getName).orElse(tableName);

        //2.获取当前表的全部字段信息
        //注意：这里是根据真实表名从本地缓存的表字段结构信息里面去拿该表的全部字段信息，如果遇到CTE语法的话，这里tableName是一个别名，拿不到，不过没关系，在WithItem维护的时候就已经维护进layerFieldTableMap中了
        List<FieldInfoDto> fieldInfoSet = Optional.ofNullable(TableCache.getTableFieldMap().get(tableName))
                .orElse(new FieldLinkedListWarpper())
                .stream()
                .map(m -> FieldInfoDto.builder().columnName(m).sourceTableName(tableName).fromSourceTable(true).rowNumber(false).sourceColumn(m).build())
                .collect(Collectors.toList());

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
        Map<String, List<FieldInfoDto>> selectTableFieldMap = this.getLayerSelectTableFieldMap().getOrDefault((this.getLayer() + 1), new FieldHashMapWrapper<>());
        //本层的字段都是来源于嵌套查询的结果集，不是真实表，所以将 fromSourceTable设置为false
        List<FieldInfoDto> fieldInfoSet = selectTableFieldMap
                .values()
                .stream()
                .flatMap(Collection::stream)
                .map(m -> FieldInfoDto.builder()
                        .fromSourceTable(false)
                        .columnName(m.getColumnName())
                        .sourceColumn(m.getSourceColumn())
                        .sourceTableName(m.getSourceTableName())
                        .rowNumber(m.isRowNumber())
                        .build())
                .collect(Collectors.toList());

        //3. 将当前层的全部字段维护进 layerFieldTableMap 中
        JsqlparserUtil.putFieldInfo(this.getLayerFieldTableMap(), this.getLayer(), aliasTable, fieldInfoSet);
    }


    @Override
    public void visit(LateralSubSelect lateralSubSelect) {
        //0.LATERAL子查询的别名，作为当前层字段的表名
        String aliasTable = Optional.ofNullable(lateralSubSelect.getAlias()).map(Alias::getName).orElse(FieldConstant.LATERAL_TABLE_ALIAS + this.getLayer());

        //1.LATERAL子查询可以访问外层字段，但是这个子查询结果独立，我们只需要子查询的最外层的结果集，所以我们使用newInstanceIndividualMap共用当前解析结果集，但是解析结果不影响当前的结果集
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceIndividualMap(this);
        Optional.ofNullable(lateralSubSelect.getSelect()).ifPresent(p -> p.accept(fieldParseTableSelectVisitor));

        //2.将LATERAL子查询的最外侧解析结果维护到 LATERAL 别名的这张表中  注意：这里获取最外层时，如果能剥离出上游作用域的解析结果时是不要上游的解析结果的，所以使用getExclusiveUpstreamScope
        Map<String, List<FieldInfoDto>> selectTableFieldMap = fieldParseTableSelectVisitor.getLayerSelectTableFieldMap().get(NumberConstant.ONE);
        if (fieldParseTableSelectVisitor.getLayerSelectTableFieldMap() instanceof LayerHashMapWrapper) {
            selectTableFieldMap = ((LayerHashMapWrapper) fieldParseTableSelectVisitor.getLayerSelectTableFieldMap()).getExclusiveUpstreamScope(NumberConstant.ONE);
        }

        //3.类型转换
        List<FieldInfoDto> fieldInfoSet = Optional.ofNullable(selectTableFieldMap)
                .orElse((Map<String, List<FieldInfoDto>>) CollectionUtils.EMPTY_MAP)
                .values()
                .stream()
                .flatMap(Collection::stream)
                .map(m -> FieldInfoDto.builder()
                        .fromSourceTable(false)
                        .columnName(m.getColumnName())
                        .sourceColumn(m.getSourceColumn())
                        .sourceTableName(m.getSourceTableName())
                        .rowNumber(m.isRowNumber())
                        .build())
                .collect(Collectors.toList());

        //4.将当前层的全部字段维护进 layerFieldTableMap 中
        JsqlparserUtil.putFieldInfo(this.getLayerFieldTableMap(), this.getLayer(), aliasTable, fieldInfoSet);
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
    }

    /**
     * from的是一个括号包裹起来的join这种语法
     * 栗如： select * from (tb_user tu join sys_user su on tu.id = su.id)的括号里面的部分
     *
     * @author liutangqi
     * @date 2026/9/4 13:42
     * @Param [aThis]
     **/
    @Override
    public void visit(ParenthesedFromItem aThis) {
        Optional.ofNullable(aThis.getFromItem()).ifPresent(p -> p.accept(this));

        List<Join> joins = Optional.ofNullable(aThis.getJoins()).orElse(CollectionUtils.EMPTY_LIST);
        for (Join join : joins) {
            Optional.ofNullable(join.getRightItem()).ifPresent(p -> p.accept(this));
        }
    }
}
