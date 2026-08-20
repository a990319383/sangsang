package com.sangsang.domain.strategy.encryptor;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 加解密策略，想要实现自定义的加解密策略的话，实现这个接口
 * 注意：目前T 仅支持 String 和 Expression类型
 *
 * @author liutangqi
 * @date 2025/6/30 17:42
 */
public interface FieldEncryptorStrategy<T> {

    /**
     * 加密算法
     *
     * @author liutangqi
     * @date 2024/4/8 14:12
     * @Param [oldExpression]
     **/
    T encryption(T oldExpression);

    /**
     * 解密算法
     *
     * @author liutangqi
     * @date 2024/4/8 14:13
     * @Param [oldExpression]
     **/
    T decryption(T oldExpression);

    /**
     * 批量解密算法
     * pojo模式下，对返回值的解密会调用此方法进行批量处理
     * PS:自定义时请根据自己实际业务场景做好分批和异常处理
     *
     * @return key:密文 value:明文
     * @author liutangqi
     * @date 2026/8/5 18:22
     * @Param [datas]
     **/
    default Map<T, T> batchDecryption(List<T> datas) {
        Map<T, T> resMap = new HashMap<>();
        if (datas == null) {
            return resMap;
        }
        for (T data : datas) {
            resMap.put(data, decryption(data));
        }
        return resMap;
    }
}
