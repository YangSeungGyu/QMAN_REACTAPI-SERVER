package com.reactapi.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.reactapi.api.kafka.KafkaController;
import com.reactapi.api.test.TestController;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class WebSecurityConfig {
	private String[] NO_AUTH_URL_PATH = {"/test", "/test/**"
			, "/common/**"
			, "/login", "/member/checkJoinMember", "/member/checkUserId","/member/checkMobileAuth"
			,"/board/getBoardList","/board/getBoardDetail"
			,"/test/getBasicGridList","/test/getPageGridList"
			,KafkaController.KAFKA_TEST_REQUEST,KafkaController.KAFKA_TEST_INIT_DATA
			
			
			//리엑트 경로
			 ,"/", "/index.html", "/assets/**", "/vite.svg"
			 
			 //웹소캣 허용 - WebSocketConfig에서 정한값
			 ,"/ws/**"
			
	};
	
	private final JwtTokenProvider jwtTokenProvider;
	private final CustomLoginSuccessHandler successHandler;
	private final CustomLoginFailureHandler failureHandler;

	public WebSecurityConfig(JwtTokenProvider jwtTokenProvider,CustomLoginSuccessHandler successHandler,
			CustomLoginFailureHandler failureHandler) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.successHandler = successHandler;
		this.failureHandler = failureHandler;
	}
	
	@Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

	@Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
	        .cors(cors -> cors.configurationSource(request -> {
	            var config = new org.springframework.web.cors.CorsConfiguration();
	            config.setAllowedOrigins(java.util.List.of("http://localhost:5173","http://localhost:5174","http://localhost:5175")); //리액트(Vite)의 주소와 포트를 정확히 적어주어야 합니다. 
	            config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
	            config.setAllowedHeaders(java.util.List.of("*"));
	            config.setAllowCredentials(true);
	            return config;
	        }))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 세션 미사용
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(NO_AUTH_URL_PATH).permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
            	    .authenticationEntryPoint((request, response, authException) -> {
            	        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); //401
            	        response.setContentType("application/json;charset=UTF-8");
            	        response.getWriter().write("{\"message\": \"로그인이 필요합니다.\"}");
            	    })
            	)
            .formLogin(form -> form
                    .loginProcessingUrl("/login")
                    .successHandler(successHandler)
                    .failureHandler(failureHandler)
             )
            .httpBasic(basic -> basic.disable())
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), 
                    org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
            

        return http.build();
    }
}