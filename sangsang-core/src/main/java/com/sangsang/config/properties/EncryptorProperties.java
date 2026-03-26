package com.sangsang.config.properties;

import com.sangsang.domain.constants.EncryptorPatternTypeConstant;
import com.sangsang.domain.constants.SymbolConstant;
import lombok.Data;

/**
 * @author liutangqi
 * @date 2024/4/8 15:22
 */
@Data
public class EncryptorProperties {
    /**
     * 秘钥，下面是默认值
     */
    private String secretKey = SymbolConstant.DEFAULT_SECRET_KEY;

    /**
     * 加解密的模式类型
     *
     * @see EncryptorPatternTypeConstant
     */
    private String patternType;
}
