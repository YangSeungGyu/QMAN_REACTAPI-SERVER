package com.reactapi.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfigurerConfig implements WebMvcConfigurer {
	//리엑트에서 f5를 누를 시 시큐어 토큰인증을 해버리는 부분 해소
	
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
  
        registry.addViewController("/{path1:[^\\.]*}")
                .setViewName("forward:/index.html");

        registry.addViewController("/{path1:[^\\.]*}/{path2:[^\\.]*}")
                .setViewName("forward:/index.html");
   
        registry.addViewController("/{path1:[^\\.]*}/{path2:[^\\.]*}/{path3:[^\\.]*}")
                .setViewName("forward:/index.html");
    }
}