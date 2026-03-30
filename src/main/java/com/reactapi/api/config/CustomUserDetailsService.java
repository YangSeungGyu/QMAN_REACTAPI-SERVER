package com.reactapi.api.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.reactapi.api.auth.service.LoginService;
import com.reactapi.api.auth.vo.CustomUser;

@Service
public class CustomUserDetailsService implements UserDetailsService {
	
	private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final LoginService loginService;

    public CustomUserDetailsService(LoginService loginService) {
        this.loginService = loginService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    	CustomUser customUser = null;
        try {
        	logger.error("username : ",username);
	        Map<String, Object> user = loginService.getUserById(username);
	        if (user == null) {
	            throw new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username);
	        }
	        var authorities = AuthorityUtils.createAuthorityList("ROLE_USER");
	        
	        customUser = new CustomUser(
	                (String) user.get("id"), 
	                (String) user.get("pw"), 
	                authorities, 
	                user
	            );
        } catch(Exception e) {
        	e.printStackTrace();
        }
        return customUser;
    }
}