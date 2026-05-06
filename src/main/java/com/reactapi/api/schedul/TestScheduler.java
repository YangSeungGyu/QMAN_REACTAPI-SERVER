package com.reactapi.api.schedul;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

//ReactApiApplication 스케줄러 어노테이션 추가
@Component
public class TestScheduler {
	
	@Autowired
	private SimpMessagingTemplate messagingTemplate;
	

	
	@Scheduled(cron = "*/3 * * * * ?") // 3초마다
	public void testScheduler01() {
		
		if(!TestSchedulerController.TEST_SCHEDUL_STATUS_01) return;
		
		String log = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")) + " 스케줄01 실행됨";
        System.out.println(log);
        messagingTemplate.convertAndSend("/topic/schedulerLog", log);
	}
	
	
	@Scheduled(cron = "*/10 * * * * ?") // 10초마다
	public void testScheduler02() {
		if(!TestSchedulerController.TEST_SCHEDUL_STATUS_02) return;
		
		String log = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")) + " 스케줄02 실행됨";
        System.out.println(log);
        messagingTemplate.convertAndSend("/topic/schedulerLog", log);
	}
}
