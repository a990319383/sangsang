package com.sangsang.transformation.oracle2mysql.plainselect;

import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.domain.constants.NumberConstant;
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
        //拿不到当前的主版本号信息 || 当前主版本< 8 ，则表示当前mysql不兼容窗口函数，需要进行转换
        return TableCache.getDataSourceConfig().getDatabaseMajorVersion() == null
                || TableCache.getDataSourceConfig().getDatabaseMajorVersion() < NumberConstant.EIGHT;
    }

    @Override
    public PlainSelectTransformationDto doTransformation(PlainSelectTransformationDto plainSelectTransformationDto) {

        return plainSelectTransformationDto;
    }
}
