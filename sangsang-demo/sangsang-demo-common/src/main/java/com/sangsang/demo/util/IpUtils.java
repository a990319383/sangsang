package com.sangsang.demo.util;

import com.sangsang.demo.domain.constants.CommonConstants;

import javax.servlet.http.HttpServletRequest;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;


/**
 * ip 处理工具类
 */
public class IpUtils {


    public static String getRealIp(HttpServletRequest request) {
        String ip = request.getHeader(CommonConstants.X_REAL_IP);
        if (ip == null || ip.length() == 0 || CommonConstants.UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader(CommonConstants.PROXY_CLIENT_IP);
        }
        if (ip == null || ip.length() == 0 || CommonConstants.UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader(CommonConstants.WL_PROXY_CLIENT_IP);
        }
        if (ip == null || ip.length() == 0 || CommonConstants.UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip.equals(CommonConstants.LOCAL) ? CommonConstants.LOCAL_IP : ip;
    }

    /**
     * 获取本地ip
     *
     * @return
     */
    public static String getLocalIp() {
        String local = "127.0.0.1";
        try {
            Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();

            while (nifs.hasMoreElements()) {
                NetworkInterface nif = nifs.nextElement();
                Enumeration<InetAddress> address = nif.getInetAddresses();
                while (address.hasMoreElements()) {
                    InetAddress addr = address.nextElement();
                    if (addr instanceof Inet4Address && !local.equals(addr.getHostAddress())) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return local;
    }
}
