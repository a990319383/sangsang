package com.sangsang.domain.constants;


/**
 * 当前项目的拦截器的顺序
 * 数字越大，越晚执行
 *
 * @author liutangqi
 * @date 2025/5/26 16:54
 */
public interface InterceptorOrderConstant {
    /**
     * sql语法转换的顺序
     * 最晚执行
     */
    int TRANSFORMATION = 100;

    /**
     * 数据权限隔离的顺序
     */
    int ISOLATION = 50;

    /**
     * 字段加解密的顺序
     * 中间执行
     **/
    int ENCRYPTOR = 50;
    /**
     * 字段脱敏的顺序
     * 必须最后执行
     */
    int DESENSITIZE = 100;

    /**
     * 给字段设置默认值
     */
    int FIELD_DEFAULT = 50;
    /**
     * 未指定order的顺序
     */
    Integer NORMAL = 50;
}
