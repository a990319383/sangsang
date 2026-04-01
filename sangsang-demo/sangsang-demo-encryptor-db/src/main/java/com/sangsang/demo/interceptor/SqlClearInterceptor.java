package com.sangsang.demo.interceptor;

import com.sangsang.demo.threadlocal.SqlHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 每次请求完成后，将SqlHolder中的内容清空
 *
 * @author liutangqi
 * @date 2026/4/1 15:45
 */
public class SqlClearInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //清除SqlHolder的内容
        SqlHolder.clear();
    }
}
