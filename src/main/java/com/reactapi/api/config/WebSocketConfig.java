package com.reactapi.api.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Key;
import java.util.Date;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
	
	// /ws로 들어오면 웹소캣 연결. 
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
        .setAllowedOrigins("http://localhost:5173","http://localhost:5174","http://localhost:5175"
        		,"http://localhost:3000","http://192.168.0.112:5173","http://192.168.0.112:5174").withSockJS();
        
        
		/* 이렇게 등록해봤자 들어오는 출입구만 다를 뿐 사실상 1개의 소캣으로 연결되는거임.
		 * registry.addEndpoint("/test_soket") .setAllowedOrigins("*");
		 */
    }

    
    /*
    기존 soket과 개념이 바뀜.
    소캣은 1개만 사용하되 그걸 내부에서 처리하기 위해
    broker와 user라는 개념이 생겼음.
    
    broker가 실제로 soket내용을 분리하는 역활(기존 소캣을 나누던것과 같은개념)을 담당하면 거기서 1:1까지 쪼갠 개념이 user
    
     
    */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
    	//브로커 등록 (그대로 쓰면 전체 클라이언트에 메시지)
        registry.enableSimpleBroker("/topic", "/testTopic" ,"/packet");
        
        //받을 경로
        registry.setApplicationDestinationPrefixes("/app");
        
        
        //1:1로 내보내는 경로
        //registry.setUserDestinationPrefix("/user");
     
    }
}