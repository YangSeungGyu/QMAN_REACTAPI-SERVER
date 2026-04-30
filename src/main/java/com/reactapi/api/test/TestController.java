package com.reactapi.api.test;

import java.util.Map;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;

@Hidden	
@RestController
public class TestController {
	
	private final SimpMessagingTemplate messagingTemplate;
	public TestController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
	
	@GetMapping("/test/test")
    public String test() {
        return "OK";
    }
	
	
	@GetMapping("/test/test2")
    public String test(@RequestParam Map<String, Object> params) {
		try {
			 String param = params.get("param").toString();
			 System.out.println(param);
			
		}catch(Exception e) {
			e.printStackTrace();
		}
        return "OK Param";
    }
	
	
	 // 소켓 테스트
    @MessageMapping("/test/testSoket") // /app/testSoket 으로 받음
    public void testSocket(@Payload String message) {
        System.out.println("받은 메시지 : " + message);
        // /testTopic/chat 구독한 클라이언트에게 응답
        
        String now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        
        messagingTemplate.convertAndSend("/testTopic", "["+now+"] OK");
    }
}
