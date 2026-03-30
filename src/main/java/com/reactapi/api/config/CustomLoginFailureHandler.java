package com.reactapi.api.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomLoginFailureHandler implements AuthenticationFailureHandler {
	
	private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        
        // 응답 설정
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

     // 1. 에러 메시지 구성
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        errorData.put("message", "아이디 또는 비밀번호가 일치하지 않습니다.");
        errorData.put("error", exception.getMessage());

        response.getWriter().write(objectMapper.writeValueAsString(errorData));
    }
}