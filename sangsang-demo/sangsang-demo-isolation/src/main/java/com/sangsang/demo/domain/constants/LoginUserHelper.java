package com.sangsang.demo.domain.constants;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author liutangqi
 * @date 2026/4/2 16:35
 */
public class LoginUserHelper {
    /**
     * key:当前登录人ip
     * value:当前登录人 org_seq
     * 备注：当前体量不需要考虑某些ip登录后不再使用导致内存无法释放的问题
     */
    private static final Map<String, String> LOGIN_USER_MAP = new ConcurrentHashMap<>();

    /**
     * 获取当前登录ip的 org_seq，拿不到默认最大权限组织
     *
     * @author liutangqi
     * @date 2026/4/2 16:42
     * @Param [ip]
     **/
    public static String getLoginUserOrgSeq(String ip) {
        return LOGIN_USER_MAP.getOrDefault(ip, "/昊天");
    }

    /**
     * 设置当前登录ip的 org_seq
     *
     * @author liutangqi
     * @date 2026/4/2 16:42
     * @Param [ip, orgSeq]
     **/
    public static void setLoginUserOrgSeq(String ip, String orgSeq) {
        LOGIN_USER_MAP.put(ip, orgSeq);
    }
}
