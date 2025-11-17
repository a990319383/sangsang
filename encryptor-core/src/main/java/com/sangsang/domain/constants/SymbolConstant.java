package com.sangsang.domain.constants;

import net.sf.jsqlparser.statement.create.table.ColDataType;

/**
 * 符号相关常量
 *
 * @author liutangqi
 * @date 2023/12/21 16:52
 */
public interface SymbolConstant {
    /**
     * 句号
     */
    String FULL_STOP = ".";
    /**
     * 逗号
     */
    String COMMA = ",";
    /**
     * 下划线
     */
    String UNDERLINE = "_";
    /**
     * 单引号
     */
    String SINGLE_QUOTES = "'";

    /**
     * 带转义符的句号
     */
    String ESC_FULL_STOP = "\\.";

    /**
     * 双引号
     */
    String DOUBLE_QUOTES = "\"";
    /**
     * 问号
     */
    String QUESTION_MARK = "?";

    /**
     * 转义的问号，用于存在正则匹配的api时，对问号的处理
     */
    String ESC_QUESTION_MARK = "\\?";

    /**
     * 百分号
     */
    String PER_CENT = "%";

    /**
     * test as 取别名
     */
    String AS = " as ";

    /**
     * 数据库别名的漂
     */
    String FLOAT = "`";

    /**
     * 空白字符
     */
    String BLANK = "";

    /**
     * 空格
     */
    String SPACING = " ";

    /**
     * 星号
     */
    String START = "*";
    /**
     * union
     */
    String UNION = "union";
    /**
     * 默认秘钥
     */
    String DEFAULT_SECRET_KEY = "7uq?q8g3@q";

    /**
     * mysql AES 加密函数
     */
    String AES_ENCRYPT = "AES_ENCRYPT";

    /**
     * mysql AES 解密函数
     */
    String AES_DECRYPT = "AES_DECRYPT";

    /**
     * mysql 转base64
     */
    String TO_BASE64 = "TO_BASE64";

    /**
     * 从base64转码
     */
    String FROM_BASE64 = "FROM_BASE64";


    /**
     * 类型转换函数想要转换的类型
     */
    ColDataType COLDATATYPE_HCAR = new ColDataType("CHAR");

    /**
     * 默认的主键id的字段名
     */
    String ID = "id";

    /**
     * 主键字段查出来的索引类型
     **/
    String PRIMARY_KEY = "PRI";

    /**
     * 数据库里面的字符串类型
     */
    String VARCHAR = "varchar";

    /**
     * 数据库里面的文本类型
     */
    String TEXT = "text";

    /**
     * 默认的时间格式化格式
     **/
    String DEFAULT_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * 默认的天的日期格式化格式
     */
    String DEFAULT_DAY_FORMAT = "yyyy-MM-dd";
}
