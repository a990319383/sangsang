package com.sangsang.transformation.oracle2mysql.plainselect;

import com.sangsang.domain.dto.PlainSelectTransformationDto;
import com.sangsang.transformation.PlainSelectTransformation;

/*
 * @author liutangqi
 * @date 2026/1/15 18:00
 */
public class RowNumberPlainSelectO2MTf extends PlainSelectTransformation {
    /**
     * 当前mysql的版本是8.0以上的话，就不用进行窗口函数的转换
     *
     * @author liutangqi
     * @date 2026/1/16 14:34
     * @Param [plainSelectTransformationDto]
     **/
    @Override
    public boolean needTransformation(PlainSelectTransformationDto plainSelectTransformationDto) {
        return false;
    }

    @Override
    public PlainSelectTransformationDto doTransformation(PlainSelectTransformationDto plainSelectTransformationDto) {
        return null;
    }
}
