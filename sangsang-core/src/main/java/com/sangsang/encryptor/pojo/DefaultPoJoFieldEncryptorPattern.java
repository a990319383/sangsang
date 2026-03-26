package com.sangsang.encryptor.pojo;

import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.symmetric.DES;
import com.sangsang.config.properties.SangSangProperties;
import com.sangsang.domain.strategy.encryptor.FieldEncryptorStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * java pojo 加密方式下的默认加解密算法
 * 默认采用DES算法
 *
 * @author liutangqi
 * @date 2024/7/24 17:41
 */
@Slf4j
public class DefaultPoJoFieldEncryptorPattern implements FieldEncryptorStrategy<String> {

    private SangSangProperties sangSangProperties;

    public DefaultPoJoFieldEncryptorPattern(SangSangProperties sangSangProperties) {
        this.sangSangProperties = sangSangProperties;
    }

    private DES des;

    @Override
    public String encryption(String cleartext) {
        //注意：这里值处理null的情况，空字符串也需要进行加密处理
        if (cleartext == null) {
            return cleartext;
        }

        String ciphertext = cleartext;
        try {
            if (des == null) {
                des = new DES(sangSangProperties.getEncryptor().getSecretKey().getBytes());
            }
            byte[] encryptBytes = des.encrypt(cleartext.getBytes());
            ciphertext = HexUtil.encodeHexStr(encryptBytes);
        } catch (Exception e) {
            log.error("【sangsang】pojo模式加密失败 cleartext:{}", cleartext, e);
        }
        return ciphertext;
    }

    @Override
    public String decryption(String ciphertext) {
        //注意：这里值处理null的情况，空字符串也需要进行加密处理
        if (ciphertext == null) {
            return ciphertext;
        }

        String cleartext = ciphertext;
        try {
            if (des == null) {
                des = new DES(sangSangProperties.getEncryptor().getSecretKey().getBytes());
            }

            byte[] decryptBytes = des.decrypt(HexUtil.decodeHex(ciphertext));
            return new String(decryptBytes);
        } catch (Exception e) {
            log.error("【sangsang】pojo模式解密失败 ciphertext:{}", ciphertext, e);
        }
        return cleartext;
    }

}