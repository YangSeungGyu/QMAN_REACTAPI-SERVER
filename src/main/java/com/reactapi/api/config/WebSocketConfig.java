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
    }

    
    //연결된 소캣을 어디로 내보내고 받을건지.
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
    	//내보내는 경로
        registry.enableSimpleBroker("/topic");
        //받는 경로
        registry.setApplicationDestinationPrefixes("/app"); // 지금은 controller url로 받아서 활용안함.
    }
}