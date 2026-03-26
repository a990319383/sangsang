package com.sangsang.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 语法转换时，不同的语法格式对应关系
 *
 * @author liutangqi
 * @date 2025/6/4 14:10
 **/
@AllArgsConstructor
@Getter
public enum TransformationDateFormatEnum {
    YEAR_4("%Y", "YYYY", "4位年份  '2025'"),
    YEAR_2("%y", "YY", "2位年份 '25'"),
    MONTH("%m", "MM", "月份(01-12)  '06'"),
    DAY("%d", "DD", "日(01-31)	'06'"),
    HOUR("%H", "HH24", "小时(00-23)	'06'"),
    MINUTE("%i", "MI", "分钟(00-59)	'06'"),
    SECOND("%s", "SS", "秒(00-59) '06'"),
    ;
    /**
     * mysql的
     **/
    private String mysql;
    /**
     * 达梦的
     **/
    private String dm;
    /**
     * 描述
     **/
    private String desc;


    /**
     * mysql的STR_TO_DATE 的格式转 达梦的日期格式
     *
     * @author liutangqi
     * @date 2025/6/4 15:04
     * @Param [format]
     **/
    public static String mysql2dmFormat(String format) {
        String res = format;
        for (TransformationDateFormatEnum dateFormatEnum : TransformationDateFormatEnum.values()) {
            res = res.replaceAll(dateFormatEnum.mysql, dateFormatEnum.dm);
        }
        return res;
    }

    /**
     * oracle 的日期格式转换为 mysql 的日期格式。
     *
     * @author liutangqi
     * @date 2026/3/24 11:32
     * @Param [format]
     **/
    public static String oracle2mysqlFormat(String format) {
        String res = format;
        for (TransformationDateFormatEnum dateFormatEnum : TransformationDateFormatEnum.values()) {
            res = res.replace(dateFormatEnum.dm, dateFormatEnum.mysql);
        }
        return res;
    }
}
