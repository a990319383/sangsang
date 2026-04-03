package com.sangsang.demo.strategies;

import com.sangsang.demo.domain.constants.LoginUserHelper;
import com.sangsang.demo.util.IpUtils;
import com.sangsang.domain.enums.IsolationRelationEnum;
import com.sangsang.domain.strategy.isolation.DataIsolationStrategy;
import org.springframework.stereotype.Component;

/**
 * @author liutangqi
 * @date 2026/4/2 16:15
 */
@Component
public class OrgIsolationStrategy implements DataIsolationStrategy<String> {

    @Override
    public String getIsolationField(String tableName) {
        //使用表字段 org_seq 进行数据隔离 （组织权限）
        return "org_seq";
    }

    @Override
    public IsolationRelationEnum getIsolationRelation(String tableName) {
        return IsolationRelationEnum.LIKE_PREFIX;
    }

    /**
     * 这里模拟从当前登录用户中拿组织字段
     * 实际项目中这个需要改造成从当前登录用户中获取
     *
     * @author liutangqi
     * @date 2026/4/2 16:46
     * @Param [tableName]
     **/
    @Override
    public String getIsolationData(String tableName) {
        //1.获取当前访问用户ip
        String localIp = IpUtils.getLocalIp();
        //2.通过ip拿到用户模拟登陆时选择的组织，拿不到默认拿最大权限组织
        return LoginUserHelper.getLoginUserOrgSeq(localIp);
    }
}
