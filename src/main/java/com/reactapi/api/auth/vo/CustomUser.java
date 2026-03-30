package com.reactapi.api.auth.vo;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.Map;

public class CustomUser extends User {
    // DB에서 가져온 추가 정보들을 담을 변수
    private final Map<String, Object> userMap;

    public CustomUser(String username, String password, 
                      Collection<? extends GrantedAuthority> authorities, 
                      Map<String, Object> userMap) {
        super(username, password, authorities);
        this.userMap = userMap;
    }

    public Map<String, Object> getUserMap() {
        return userMap;
    }
}