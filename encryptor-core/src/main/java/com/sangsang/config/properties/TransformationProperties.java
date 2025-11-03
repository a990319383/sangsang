package com.sangsang.config.properties;

import com.sangsang.domain.constants.TransformationPatternTypeConstant;
import lombok.Data;

/**
 * @author liutangqi
 * @date 2025/5/21 16:13
 */
@Data
public class TransformationProperties {
    /**
     * 当前转换类型
     * 注意：做扩展时，这个命名必须和com.sangsang.transformation这个路径的下一级包名一致
     * 例如：想实现mysql2oracle的扩展，则在com.sangsang.transformation这个路径的下一级建立一个mysql2oracle包，然后在这个路径下实现所有转换器
     *
     * @see TransformationPatternTypeConstant
     */
    private String patternType;

    /**
     * 语法转换时是否将字段和表名强制转换为小写
     * 默认是否
     * 开启时会将字段和表名强制转换为小写，并且使用双引号引起来
     **/
    private boolean forcedLowercase = false;
}
