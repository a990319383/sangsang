package com.sangsang.demo.strategies;

import com.sangsang.domain.strategy.encryptor.FieldEncryptorStrategy;
import org.springframework.stereotype.Component;

/**
 * 相同密文碰撞复现使用的第一个策略
 */
@Component
public class CollisionFirstFieldEncryptorStrategy implements FieldEncryptorStrategy<String> {

    @Override
    public String encryption(String cleartext) {
        return cleartext;
    }

    @Override
    public String decryption(String ciphertext) {
        System.out.println("aaaaaaaaaaaaaa");
        return "strategy-a-plaintext";
    }
}
