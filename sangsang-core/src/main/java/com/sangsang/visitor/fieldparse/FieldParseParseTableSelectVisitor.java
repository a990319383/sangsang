package com.sangsang.visitor.fieldparse;

import com.sangsang.domain.constants.NumberConstant;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.wrapper.LayerHashMapWrapper;
import com.sangsang.util.CollectionUtils;
import com.sangsang.util.DeepCloneUtil;
import com.sangsang.util.JsqlparserUtil;
import com.sangsang.util.visitor.FieldParseVisitorUtil;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.statement.select.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * select 语句解析每一层拥有的表和拥有的全部字段解析入口
 *
 * @author liutangqi
 * @date 2024/3/4 10:32
 */
public class FieldParseParseTableSelectVisitor extends BaseFieldParseTable implements SelectVisitor {

    /**
     * 获取第一层对象
     *
     * @author liutangqi
     * @date 2025/3/4 16:47
     * @Param [baseFieldParseTable]
     **/
    public static FieldParseParseTableSelectVisitor newInstanceFirstLayer() {
        return new FieldParseParseTableSelectVisitor(
                NumberConstant.ONE,
                null,
                null
        );
    }


    public static FieldParseParseTableSelectVisitor newInstanceFirstLayer(Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        return new FieldParseParseTableSelectVisitor(
                NumberConstant.ONE,
                layerSelectTableFieldMap,
                layerFieldTableMap
        );
    }

    /**
     * 获取下一层对象
     *
     * @author liutangqi
     * @date 2025/3/4 16:47
     * @Param [baseFieldParseTable]
     **/
    public static FieldParseParseTableSelectVisitor newInstanceNextLayer(BaseFieldParseTable baseFieldParseTable) {
        return new FieldParseParseTableSelectVisitor(
                (baseFieldParseTable.getLayer() + 1),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap()
        );
    }

    /**
     * 创建一个实例，共用同一个层数，但是将存储数据的Map拷贝一份，采用单独的空间存储重要信息，和之前的数据存储独立开来
     * 当前层的表字段信息，作为上游作用域传递到下层，部分语法不严格的数据库，下层的所有层级都有权限访问这层表字段信息
     * 使用场景：一般用于独立的子查询，这个子查询的解析结果不能和父层级共享的时候使用
     * 栗子：
     * select
     * (select 字段 from xx ) -- 这一坨的子查询的解析结果不应该和父层级共存，不管把这部分放哪层都是有问题的
     * from
     * 这种语法
     *
     * @author liutangqi
     * @date 2025/3/4 17:01
     * @Param [layer, layerSelectTableFieldMap, layerFieldTableMap]
     **/
    public static FieldParseParseTableSelectVisitor newInstanceIndividualMap(BaseFieldParseTable baseFieldParseTable) {
        //将现在的两个存储解析结果的map深克隆拷贝一份，用这两份数据去解析子查询的结果，避免这个子查询也拥有子查询，导致影响当前解析结果的map的下一层结果出错
        Map<Integer, Map<String, List<FieldInfoDto>>> cloneLayerSelectTableFieldMap = DeepCloneUtil.MapClone.cloneIntKey(baseFieldParseTable.getLayerSelectTableFieldMap());
        Map<Integer, Map<String, List<FieldInfoDto>>> cloneLayerFieldTableMap = DeepCloneUtil.MapClone.cloneIntKey(baseFieldParseTable.getLayerFieldTableMap());
        //将当前层的表字段信息作为上游作用域传递到下层
        LayerHashMapWrapper LayerSelectTableFieldMapWrapper = new LayerHashMapWrapper(cloneLayerSelectTableFieldMap.get(baseFieldParseTable.getLayer()));
        LayerHashMapWrapper LayerFieldTableMapWrapper = new LayerHashMapWrapper(cloneLayerFieldTableMap.get(baseFieldParseTable.getLayer()));
        return new FieldParseParseTableSelectVisitor(baseFieldParseTable.getLayer(), LayerSelectTableFieldMapWrapper, LayerFieldTableMapWrapper);
    }

    private FieldParseParseTableSelectVisitor(int layer, Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    /**
     * in (select xxx from ) -- (子查询)这部分
     *
     * @author liutangqi
     * @date 2025/3/12 15:53
     * @Param [parenthesedSelect]
     **/
    @Override
    public void visit(ParenthesedSelect parenthesedSelect) {
        parenthesedSelect.getSelect().accept(this);
    }

    @Override
    public void visit(PlainSelect plainSelect) {
        //解析CTE语法 栗如： WITH x AS (SELECT * FROM tb_user)
        FieldParseVisitorUtil.cte(plainSelect.getWithItemsList(), this);

        // from 的表
        FromItem fromItem = plainSelect.getFromItem();
        if (fromItem != null) {
            FieldParseParseTableFromItemVisitor fieldParseTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceCurLayer(this);
            fromItem.accept(fieldParseTableFromItemVisitor);
        }


        //join 的表
        List<Join> joins = Optional.ofNullable(plainSelect.getJoins()).orElse(new ArrayList<>());
        for (Join join : joins) {
            FromItem rightItem = join.getRightItem();
            FieldParseParseTableFromItemVisitor joinFieldTableFromItemVisitor = FieldParseParseTableFromItemVisitor.newInstanceCurLayer(this);
            rightItem.accept(joinFieldTableFromItemVisitor);
        }

        //查询的全部字段
        List<SelectItem<?>> selectItems = plainSelect.getSelectItems();
        for (SelectItem selectItem : selectItems) {
            FieldParseParseSelectItemVisitor fieldParseSelectItemVisitor = FieldParseParseSelectItemVisitor.newInstanceCurLayer(this);
            selectItem.accept(fieldParseSelectItemVisitor);
        }
    }

    /**
     * union all
     * union
     * 在使用数据库本身的函数加解密的模式下，这种语法的解析没有必要，不会使用这个解析结果的，union的几条sql都是单独解析，进行加解密处理的
     * 在使用java pojo 进行加解密的模式下，我们需要知道每个字段对应的表，才知道是否需要加解密，这里只解析union的第一个sql,因为对于标准的数据安全的场景，只要这个字段是需要加密的，那这个字段涉及的所有表都是应该加解密的，所以我们只解析第一张表的字段归属
     *
     * @author liutangqi
     * @date 2024/3/6 13:58
     * @Param [setOpList]
     **/
    @Override
    public void visit(SetOperationList setOpList) {
        // WITH 语句挂在 SetOperationList 上时，需要先解析 CTE 定义
        FieldParseVisitorUtil.cte(setOpList.getWithItemsList(), this);

        List<Select> selects = setOpList.getSelects();
        if (CollectionUtils.isEmpty(selects)) {
            return;
        }
        Select selectBody = selects.get(0);
        selectBody.accept(this);
    }

    /**
     * 解析CTE语法 栗如： WITH x AS (SELECT id,name FROM tb_user)
     * 注意1：CTE的内部表达式是一个独立的sql，这里面不会访问外部作用域，所以解析的时候单独创建一个visitor进行解析
     * 注意2：CTE的内部表达式的第一层作用域的字段结果集，就是这个CET表达式外部能访问的所有字段:栗子中x这个表这层拥有的全部字段就是id和name
     *
     * @author liutangqi
     * @date 2026/9/4 13:10
     * @Param [withItem]
     **/
    @Override
    public void visit(WithItem withItem) {
        //CTE 缺少内部表达式的错误语法，不处理
        if (withItem.getSelect() == null) {
            return;
        }

        //获取CTE的别名，正确语法下别名是必须有的
        String aliasTable = Optional.ofNullable(withItem.getAlias()).map(Alias::getName).orElse(null);
        if (aliasTable == null) {
            return;
        }

        // 将CTE 内部语法属于单独的作用域,所以这里新建一个全新的visitor进行字段解析
        FieldParseParseTableSelectVisitor fieldParseTableSelectVisitor = FieldParseParseTableSelectVisitor.newInstanceFirstLayer();
        withItem.getSelect().accept(fieldParseTableSelectVisitor);

        //上面解析完成后的第一层结果作为当前CTE的拥有字段
        Map<String, List<FieldInfoDto>> selectTableFieldMap = fieldParseTableSelectVisitor.getLayerSelectTableFieldMap().getOrDefault(NumberConstant.ONE, CollectionUtils.EMPTY_MAP);
        List<FieldInfoDto> fieldInfoSet = selectTableFieldMap.values()
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

        //将上面解析的第一层结果集，放到当前sql这层作用域中
        JsqlparserUtil.putFieldInfo(this.getLayerFieldTableMap(), this.getLayer(), aliasTable, fieldInfoSet);
    }

    @Override
    public void visit(Values aThis) {

    }

    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }

    @Override
    public void visit(TableStatement tableStatement) {

    }
}
