package com.sangsang.domain.dto;

import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.enums.EncryptorFunctionEnum;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 需要进行加解密函数调用的基类
 *
 * @author liutangqi
 * @date 2025/3/1 12:28
 */
@Getter
public class BaseDEcryptParseTable extends BaseFieldParseTable {
    /**
     * 当前字段需要密文存储时，应该调用加密方法还是解密方法
     *
     * @author liutangqi
     * @date 2025/2/28 23:12
     * @Param
     **/
    private EncryptorFunctionEnum encryptorFunctionEnum;

    /**
     * 和当前表达式对应的上游字段标注的注解
     * 当此表达式的加解密处理受上游字段影响时才有值
     *
     * @author liutangqi
     * @date 2025/6/25 18:24
     * @Param
     **/
    private FieldEncryptor upstreamFieldEncryptor;

    public BaseDEcryptParseTable(int layer,
                                 EncryptorFunctionEnum encryptorFunctionEnum,
                                 FieldEncryptor upstreamFieldEncryptor,
                                 Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap,
                                 Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
        this.encryptorFunctionEnum = encryptorFunctionEnum;
        this.upstreamFieldEncryptor = upstreamFieldEncryptor;
    }


}
