package com.sangsang.domain.constants;

/**
 * 加密模式常量
 *
 * @author liutangqi
 * @date 2024/7/26 9:17
 */
public interface EncryptorPatternTypeConstant {
    /**
     * 利用数据库本身的库函数进行加解密
     */
    String DB = "db";
    /**
     * 利用java的函数对入参响应进行加解密
     * 这种模式支持多种加解密算法
     */
    String POJO = "pojo";
}
