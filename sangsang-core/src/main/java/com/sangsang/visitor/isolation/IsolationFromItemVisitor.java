package com.sangsang.visitor.isolation;

import com.sangsang.domain.dto.BaseFieldParseTable;
import com.sangsang.domain.dto.FieldInfoDto;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author liutangqi
 * @date 2025/6/13 14:36
 */
public class IsolationFromItemVisitor extends BaseFieldParseTable implements FromItemVisitor {

    /**
     * 获取当前层实例
     *
     * @author liutangqi
     * @date 2025/6/13 14:36
     * @Param [baseFieldParseTable]
     **/
    public static IsolationFromItemVisitor newInstanceCurLayer(BaseFieldParseTable baseFieldParseTable) {
        return new IsolationFromItemVisitor(baseFieldParseTable.getLayer(),
                baseFieldParseTable.getLayerSelectTableFieldMap(),
                baseFieldParseTable.getLayerFieldTableMap());
    }

    private IsolationFromItemVisitor(int layer, Map<Integer, Map<String, List<FieldInfoDto>>> layerSelectTableFieldMap, Map<Integer, Map<String, List<FieldInfoDto>>> layerFieldTableMap) {
        super(layer, layerSelectTableFieldMap, layerFieldTableMap);
    }

    @Override
    public void visit(Table tableName) {

    }

    /**
     * 嵌套子查询
     *
     * @author liutangqi
     * @date 2025/6/13 14:37
     * @Param [selectBody]
     **/
    @Override
    public void visit(ParenthesedSelect selectBody) {
        //注意：这里存在嵌套，所以是下一层
        Optional.ofNullable(selectBody.getSelect())
                .ifPresent(p -> p.accept(IsolationSelectVisitor.newInstanceNextLayer(this)));
    }

    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }

    @Override
    public void visit(TableFunction tableFunction) {

    }

    /**
     * from的是一个括号包裹起来的join这种语法
     * 栗如： select * from (tb_user tu join sys_user su on tu.id = su.id)的括号里面的部分
     * 这个语法和本功能无任何关联
     *
     * @author liutangqi
     * @date 2026/9/4 13:42
     * @Param [aThis]
     **/
    @Override
    public void visit(ParenthesedFromItem aThis) {

    }
}
