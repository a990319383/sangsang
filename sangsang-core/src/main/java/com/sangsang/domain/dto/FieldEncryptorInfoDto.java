package com.sangsang.domain.dto;

import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段所属表信息，加密注解信息
 *
 * @author liutangqi
 * @date 2024/7/8 10:23
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FieldEncryptorInfoDto {
    /**
     * 字段的别名或者是原字段名
     * 注意：这个能不转换为小写，转换为小写后会影响别名的驼峰
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
     * 该字段上标准的注解
     */
    private FieldEncryptor fieldEncryptor;
}
