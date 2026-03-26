package com.sangsang.domain.enums;

import com.sangsang.domain.function.EncryptorFunction;
import com.sangsang.domain.function.EncryptorFunctionScene;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author liutangqi
 * @date 2025/3/3 10:18
 */
@AllArgsConstructor
@Getter
public enum EncryptorFunctionEnum {

    DEFAULT_ENCRYPTION("不管上游对应字段，默认加密", EncryptorFunctionScene.defaultEncryption()),
    DEFAULT_DECRYPTION("不管上游对应字段，默认解密", EncryptorFunctionScene.defaultDecryption()),
    UPSTREAM_COLUMN("存在上游对应字段，需要根据情况处理", EncryptorFunctionScene.upstreamColumn()),
    ;
    /**
     * 描述
     */
    private String desc;

    /**
     * 具体加解密执行的逻辑
     */
    private EncryptorFunction fun;
}
