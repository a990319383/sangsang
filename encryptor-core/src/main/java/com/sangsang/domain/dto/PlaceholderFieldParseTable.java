package com.sangsang.domain.dto;

import com.sangsang.domain.wrapper.FieldHashMapWrapper;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 需要解析占位符所属表结构信息的基类
 *
 * @author liutangqi
 * @date 2024/7/12 10:41
 */
@Getter
public class PlaceholderFieldParseTable extends BaseFieldParseTable {
    /**
     * 当前占位符对应的数据库表，字段信息 （存放解析的结果集）
     * key: 占位符DecryptConstant.PLACEHOLDER + 0开始的自增序号  （这个在解析前，我们会将？的占位符统一替换成这个格式的占位符）
     * value: 这个字段所属的表字段
     */
    private Map<String, ColumnTableDto> placeholderColumnTableMap;

    public PlaceholderFieldParseTable(int layer, Map<String, Map<String, Set<FieldInfoDto>>> layerSelectTableFieldMap, Map<String, Map<String, Set<FieldInfoDto>>> layerFieldTableMap, Map<String, ColumnTableDto> placeholderColumnTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
        this.placeholderColumnTableMap = Optional.ofNullable(placeholderColumnTableMap).orElse(new FieldHashMapWrapper<>());
    }

    public PlaceholderFieldParseTable(BaseFieldParseTable baseFieldParseTable, Map<String, ColumnTableDto> placeholderColumnTableMap) {
        super(baseFieldParseTable.getLayer(), baseFieldParseTable.getLayerSelectTableFieldMap(), baseFieldParseTable.getLayerFieldTableMap());
        this.placeholderColumnTableMap = Optional.ofNullable(placeholderColumnTableMap).orElse(new FieldHashMapWrapper<>());
    }

}
