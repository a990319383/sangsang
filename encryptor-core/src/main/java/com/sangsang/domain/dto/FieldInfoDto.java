package com.sangsang.domain.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 *
 * @author liutangqi
 * @date 2024/3/6 10:30
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FieldInfoDto implements Serializable {
    /**
     * 字段的别名或者是原字段名
     * 注意：这个不能转换为小写，转换为小写后会影响别名的驼峰
     **/
    private String columnName;
    /**
     * 该字段来源自哪个字段
     */
    private String sourceColumn;
    /**
     * 字段取自数据库的那张表的表名
     */
    private String sourceTableName;

    /**
     * 该字段是否直接从真实的表中关联获取的
     * 栗子：select user_name   from tb_user    user_name 这个字段真实属于tb_user的，这个值就是ture
     * select a.userName from (select user_name   from tb_user )a    userName这个字段是来自于表a 的，表a不是真实的数据来源表，所以这个值是false
     **/
    @Builder.Default
    private boolean fromSourceTable = false;
}
