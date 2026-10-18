package com.shike.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 内部接口的共享密钥校验 —— AI 内部接口三层防护的第 ② 层。
 *
 * ============================================================
 * 为什么需要它
 * ============================================================
 * /internal/** 不在 JWT 拦截器范围内（Python 手上没有用户 token），
 * 也就是说任何能访问 8080 的人都能直接查菜单、加购物车。
 *
 * 三层防护：
 *   ① server.address = 127.0.0.1   外网连端口都连不上（prod 已配）
 *   ② 本拦截器：校验共享密钥       即使端口暴露，没密钥也调不动
 *   ③ 云安全组不放行 8080          外围第一道
 *
 * 为什么用"共享密钥"而不是 JWT：
 *   Python 是一个服务，不是某个用户。它是"可信的内部调用方"，
 *   不存在"登录"这个动作，用一把长期密钥更贴合。
 *
 * ============================================================
 * ★ 空值 = 不校验
 * ============================================================
 * shike.internal.token 不配时直接放行 ——
 *   本地开发不用配、不会因为忘配而全部 403；
 *   生产在 application-prod.yml 里配上，校验才生效。
 */
@Component
@Slf4j
public class InternalTokenInterceptor implements HandlerInterceptor {

    @Value("${shike.internal.token:}")
    private String expectedToken;

    @Override
    public boolean preHandle(HttpServletRequest request,
                            HttpServletResponse response,
                            Object handler) throws Exception {

        // 没配置密钥 → 不校验（本地开发）
        if (!StringUtils.hasText(expectedToken)) {
            return true;
        }

        String actual = request.getHeader("X-Internal-Token");
        if (expectedToken.equals(actual)) {
            return true;
        }

        // 不要打印 actual —— 那等于把别人试的密钥写进日志
        log.warn("内部接口鉴权失败：path={}, 是否带了 header={}",
                request.getRequestURI(), actual != null);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":0,\"msg\":\"内部接口鉴权失败\"}");
        return false;
    }
}
