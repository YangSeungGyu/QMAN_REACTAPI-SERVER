package com.reactapi.api.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reactapi.api.auth.service.LoginService;
import com.reactapi.api.auth.vo.CustomUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final LoginService loginService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 생성자를 직접 작성하여 의존성을 주입받습니다.
    public CustomLoginSuccessHandler(JwtTokenProvider jwtTokenProvider, LoginService loginService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginService = loginService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
    	
    	CustomUser customUser = (CustomUser) authentication.getPrincipal();
    	
        String username = authentication.getName();
        String token = jwtTokenProvider.createToken(username);

        
        Map<String, Object> responseData = new HashMap<>();
        Map<String, Object> user =  customUser.getUserMap();
        responseData.put("accessToken", token);
        responseData.put("id", user.get("id"));
        responseData.put("nm", user.get("nm"));
        responseData.put("message", "로그인 성공");

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(objectMapper.writeValueAsString(responseData));
    }
}