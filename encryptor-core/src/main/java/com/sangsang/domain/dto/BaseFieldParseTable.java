package com.sangsang.domain.dto;


import com.sangsang.domain.wrapper.LayerHashMapWrapper;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 解析sql中出现字段的基类
 *
 * @author liutangqi
 * @date 2024/3/4 14:26
 */
@Getter
public class BaseFieldParseTable {
    /**
     * 层数
     */
    private int layer;
    /**
     * 存储sql中 第layer 层select 中出现字段
     * key： layer层数
     * value:
     * ---key: 表别名(有表别名就是别名，没有别名就是表名)
     * --- value: (注意：这里嵌套子查询时，这里的所有字段不一定属于同一张表)
     * -------出现的字段的 “别名” 和这个字段所属的真实表名
     **/
    private Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap;
    /**
     * 存储sql中 第layer 层查询表 拥有的全部字段
     * key： layer层数
     * value:
     * ---key: 表别名(有表别名就是别名，没有别名就是表名)
     * --- value: (注意：这里嵌套子查询时，这里的所有字段不一定属于同一张表)
     * ------查询的表拥有的全部 “字段原名”和所属的真实表名
     *
     * 注意：sql中出现的某些常量字段，或者数据库本身特性凭空产生的字段，比如rownumber，只会存在 layerSelectTableFieldMap中，layerFieldTableMap里面是不存储这部分信息的
     */
    private Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap;


    protected static final Logger log = LoggerFactory.getLogger(BaseFieldParseTable.class);

    public BaseFieldParseTable(int layer,
                               Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap,
                               Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        this.layer = layer;
        this.layerSelectTableFieldMap = Optional.ofNullable(layerSelectTableFieldMap).orElse(new LayerHashMapWrapper());
        this.layerFieldTableMap = Optional.ofNullable(layerFieldTableMap).orElse(new LayerHashMapWrapper());
    }

}
