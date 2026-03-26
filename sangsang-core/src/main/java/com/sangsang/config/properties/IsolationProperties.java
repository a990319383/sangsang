package com.sangsang.config.properties;

import com.sangsang.domain.enums.IsolationConditionalRelationEnum;
import lombok.Data;

/**
 * 数据隔离的配置
 *
 * @author liutangqi
 * @date 2025/6/13 10:16
 */
@Data
public class IsolationProperties {
    /**
     * 是否开启数据隔离功能
     */
    private boolean enable = false;

    /**
     * 同一个sql，不同字段直接数据隔离的关系，默认and
     *
     * @see IsolationConditionalRelationEnum
     **/
    private IsolationConditionalRelationEnum conditionalRelation = IsolationConditionalRelationEnum.AND;

    /**
     * 当隔离条件使用in的时候，因为数据库有参数长度限制，不分段的话会报错，这里是每段的长度
     * 栗子： 原本是  id in (xxx,xxx,xxx 共计1000个)，分段后 (id in (xxx,xxx共计500个) or id in (xxx,xxx共计500个))
     */
    private Integer inRelationSubsectionSize = 500;

    /**
     * 是否开启DML数据隔离功能
     * 执行插入，修改，删除语句时，是否进行数据隔离条件的拼接
     * 默认是否，因为实际业务中一般这些操作都会带上唯一键或者业务唯一标识，所以默认关闭，避免增加不必要的耗时
     */
    private boolean supportDML = false;

}
