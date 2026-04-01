package com.sangsang.demo.domain.constants;

public interface CommonConstants {


    String X_REAL_IP = "X-Real-IP";

    /**
     * x-forwarded-for
     */
    String X_FORWARDED_FOR = "x-forwarded-for";

    /**
     * unknown
     */
    String UNKNOWN = "unknown";

    /**
     * Proxy-Client-IP
     */
    String PROXY_CLIENT_IP = "Proxy-Client-IP";

    /**
     * WL-Proxy-Client-IP
     */
    String WL_PROXY_CLIENT_IP = "WL-Proxy-Client-IP";

    /**
     * local
     */
    String LOCAL = "0:0:0:0:0:0:0:1";

    /**
     * local_ip
     */
    String LOCAL_IP = "127.0.0.1";

    /**
     * all
     */
    String ALL = "all";


    String STAR = "*";

    String PROTOCOL = "http://";

    String COLON = ":";

    String SLASH = "/";

    /**
     * spel表达式的变量名
     */
    String SPEL_VARIABLE_NAME = "param";
    /**
     * 使用？.的方式，可以避免空值的判断问题
     */
    String SPEL_SEPARATOR = "?.";
    /**
     * 井号
     */
    String WELL_NUMBER = "#";
}
