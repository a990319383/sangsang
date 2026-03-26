package com.sangsang.visitor.dbencrtptor;

import com.sangsang.domain.annos.encryptor.FieldEncryptor;
import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * @author liutangqi
 * @date 2024/2/29 16:07
 */
public class DBDecryptFromItemVisitor extends BaseFieldParseTable implements FromItemVisitor {

    /**
     * 当涉及到上游不同字段需要进行不同的加解密场景时。这个字段传上游的需要加密的项的索引下标的@FieldEncryptor
     * 当有些场景嵌套查询也包了一层嵌套这个就需要传递过来
     * 例如：
     * 场景1：insert(a,b,c) select tmp.* from (select( x,y,z))tmp 当 a,c需要密文存储，b 不需要时，这值就是[@FieldEncryptor,null,@FieldEncryptor]
     * 场景2：xxx in(select tmp.* from(select x,y,z)tmp) ，当xxx 需要密文存储时，这个值就是[@FieldEncryptor]
     * 不涉及到这个项在上游有对应的列时，这个字段为null
     */
    private List<FieldEncryptor> upstreamNeedEncryptFieldEncryptor;

    /**
     * 获取当前层的解析对象
     *
     * @author liutangqi
     * @date 2025/3/4 15:44
     * @Param [baseFieldParseTable]
     **/
    public static DBDecryptFromItemVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable, List<FieldEncryptor> upstreamNeedEncryptFieldEncryptor) {
        return new DBDecryptFromItemVisitor(baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap(),
                upstreamNeedEncryptFieldEncryptor);
    }

    /**
     * 获取当前层的解析对象
     * 不需要和上游数据关联
     *
     * @author liutangqi
     * @date 2025/12/22 11:05
     * @Param [baseFieldParseTable]
     **/
    public static DBDecryptFromItemVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new DBDecryptFromItemVisitor(baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap(),
                null);
    }

    private DBDecryptFromItemVisitor(int layer, Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap, List<FieldEncryptor> upstreamNeedEncryptFieldEncryptor) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
        this.upstreamNeedEncryptFieldEncryptor = upstreamNeedEncryptFieldEncryptor;
    }

    @Override
    public void visit(Table table) {
    }


    /**
     * 嵌套子查询 再解析里层的内容
     *
     * @author liutangqi
     * @date 2024/2/29 16:13
     * @Param [subSelect]
     **/
    @Override
    public void visit(ParenthesedSelect subSelect) {
        //解密子查询内容（注意：这里是下一层的）
        Optional.ofNullable(subSelect.getSelect())
                .ifPresent(p -> p.accept(DBDecryptSelectVisitor.newInstanceNextLayer(this, this.upstreamNeedEncryptFieldEncryptor)));
    }

    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }


    @Override
    public void visit(TableFunction tableFunction) {

    }

    @Override
    public void visit(ParenthesedFromItem aThis) {

    }

}
