package com.sangsang.domain.dto;

import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.enums.EncryptorFunctionEnum;
import lombok.Getter;
import net.sf.jsqlparser.schema.Table;

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

    /**
     * 某些DML语法中，可以支持特定伪表来输出返回值，比如SqlServer 的OUTPUT语法 (注意：PostgreSQL 的 RETURNING 语法是不存在伪表的，直接就是字段名，所以不需要额外处理)
     * 此时可以通过特别的伪表的伪别名获取表字段信息，FieldParser解析的信息里面是没有伪表的数据的，伪表的字段其实就是所属Table的字段
     * 这种情况下，就需要上游把所属的表给传递过来
     */
    private Table outputTable;

    public BaseDEcryptParseTable(int layer,
                                 EncryptorFunctionEnum encryptorFunctionEnum,
                                 FieldEncryptor upstreamFieldEncryptor,
                                 Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap,
                                 Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap,
                                 Table outputTable) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
        this.encryptorFunctionEnum = encryptorFunctionEnum;
        this.upstreamFieldEncryptor = upstreamFieldEncryptor;
        this.outputTable = outputTable;
    }


}
