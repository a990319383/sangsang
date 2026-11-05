package com.sangsang.visitor.fielddefault;

import com.sangsang.domain.annos.fielddefault.FieldDefault;
import com.sangsang.domain.dto.FieldInfoDto;
import com.sangsang.domain.enums.SqlCommandEnum;
import com.sangsang.util.visitor.FieldDefaultVisitorUtil;
import net.sf.jsqlparser.statement.merge.MergeDelete;
import net.sf.jsqlparser.statement.merge.MergeInsert;
import net.sf.jsqlparser.statement.merge.MergeOperationVisitor;
import net.sf.jsqlparser.statement.merge.MergeUpdate;

import java.util.List;
import java.util.Map;

/**
 * MERGE语句 matched/not matched 分支的字段默认值处理
 *
 * @author liutangqi
 * @date 2026/11/4 20:20
 */
public class FieldDefaultMergeOperationVisitor implements MergeOperationVisitor {

    /**
     * 目标表当前层的字段解析结果
     */
    private Map<String, List<FieldInfoDto>> fieldInfoDtoMap;

    /**
     * 目标表字段对应的@FieldDefault信息
     */
    private Map<String, FieldDefault> stringFieldDefaultMap;

    public FieldDefaultMergeOperationVisitor(Map<String, List<FieldInfoDto>> fieldInfoDtoMap,
                                             Map<String, FieldDefault> stringFieldDefaultMap) {
        this.fieldInfoDtoMap = fieldInfoDtoMap;
        this.stringFieldDefaultMap = stringFieldDefaultMap;
    }

    @Override
    public void visit(MergeDelete mergeDelete) {

    }

    @Override
    public void visit(MergeUpdate mergeUpdate) {
        //MATCHED UPDATE分支属于数据修改，使用UPDATE阶段的默认值策略
        FieldDefaultVisitorUtil.updateSets(mergeUpdate.getUpdateSets(), this.fieldInfoDtoMap, SqlCommandEnum.UPDATE);
    }

    @Override
    public void visit(MergeInsert mergeInsert) {
        //NOT MATCHED INSERT分支属于数据新增，使用INSERT阶段的默认值策略
        FieldDefaultVisitorUtil.insertColumnsAndValues(mergeInsert.getColumns(), mergeInsert.getValues(), this.stringFieldDefaultMap);
    }
}
