package com.sangsang.demo.strategies;

import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.symmetric.DES;
import com.sangsang.config.properties.SangSangProperties;
import com.sangsang.domain.annos.DefaultStrategy;
import com.sangsang.domain.strategy.encryptor.FieldEncryptorStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author liutangqi
 * @date 2026/4/3 15:29
 */
@Component
//设置此加密策略为默认策略，则@FieldEncryptor注解可以不指定策略
@DefaultStrategy
@RequiredArgsConstructor
@Slf4j
public class PoJoFieldEncryptorStrategy implements FieldEncryptorStrategy<String> {
    //构造注入SangSangProperties，最好以这个配置里面的秘钥作为加密密钥
    private final SangSangProperties sangSangProperties;

    /**
     * 这里自定义加密算法逻辑，包括空值处理和异常处理可根据业务自定义
     *
     * @author liutangqi
     * @date 2026/4/3 15:56
     * @Param [cleartext]
     **/
    @Override
    public String encryption(String cleartext) {
        //注意：这里值处理null的情况，空字符串也需要进行加密处理
        if (cleartext == null) {
            return cleartext;
        }

        String ciphertext = null;
        try {
            DES des = new DES(sangSangProperties.getEncryptor().getSecretKey().getBytes());
            byte[] encryptBytes = des.encrypt(cleartext.getBytes());
            ciphertext = HexUtil.encodeHexStr(encryptBytes);
            log.info("【sangsang】pojo模式加密成功 cleartext:{} ciphertext:{}", cleartext, ciphertext);
        } catch (Exception e) {
            log.error("【sangsang】pojo模式加密失败 cleartext:{}", cleartext, e);
        }
        return ciphertext;
    }

    /**
     * 这里自定义解密算法逻辑，包括空值处理和异常处理可根据业务自定义
     *
     * @author liutangqi
     * @date 2026/4/3 15:56
     * @Param [cleartext]
     **/
    @Override
    public String decryption(String ciphertext) {
        //注意：这里值处理null的情况，空字符串也需要进行加密处理
        if (ciphertext == null) {
            return ciphertext;
        }

        String cleartext = null;
        try {
            DES des = new DES(sangSangProperties.getEncryptor().getSecretKey().getBytes());

            byte[] decryptBytes = des.decrypt(HexUtil.decodeHex(ciphertext));
            cleartext = new String(decryptBytes);
            log.info("【sangsang】pojo模式解密成功 cleartext:{} ciphertext:{}", cleartext, ciphertext);
            return cleartext;
        } catch (Exception e) {
            log.error("【sangsang】pojo模式解密失败 ciphertext:{}", ciphertext, e);
        }
        return cleartext;
    }

}
