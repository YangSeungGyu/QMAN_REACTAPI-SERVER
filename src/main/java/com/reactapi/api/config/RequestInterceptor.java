package com.reactapi.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RequestInterceptor implements HandlerInterceptor {

    // 직접 Logger 선언 (롬복의 @Slf4j가 해주는 일)
    private static final Logger logger = LoggerFactory.getLogger(RequestInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();
        
        logger.info(">>> [API Request Start] {} {}", method, requestURI);
        logger.info(">>> [Client IP] {}", request.getRemoteAddr());
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        logger.info("<<< [API Request End] {} - Status: {}", request.getRequestURI(), response.getStatus());
        if (ex != null) {
            logger.error("!!! [API Error] ", ex);
        }
    }
}