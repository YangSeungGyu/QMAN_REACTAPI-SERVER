package com.reactapi.api.kafka;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;


@RestController
public class KafkaController {
	
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	@Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
	
	   //웹소캣 전용
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
	
	public static final String KAFKA_TEST_REQUEST = "/kafka/kafkaTestRequest";
	public static final String KAFKA_TEST_INIT_DATA = "/kafka/kafkaTestInitData";
	
	private final List<Map<String, Object>> messageStorage = new CopyOnWriteArrayList<>();
	

	//카프카에 메시지 보내기
	@PostMapping(KAFKA_TEST_REQUEST)
    public Map<String, Object> KAFKA_TEST_REQUEST(@RequestBody Map<String, Object> params) {
        Map<String, Object> resultMap = new HashMap<>();
        log.debug("url : {}",KAFKA_TEST_REQUEST);
        try {
        	kafkaTemplate.send("my-test-topic", params);
            resultMap.put("result", "success");
            System.out.println("Sent to Kafka: " + params);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return resultMap;
    }
	
	
	
	//페이지 진입시 전체 리스트 가져오기
	@PostMapping(KAFKA_TEST_INIT_DATA)
    public Map<String, Object> kafkaTestInitData() {
		log.debug("url : {}",KAFKA_TEST_INIT_DATA);
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("result", "success");
        resultMap.put("data", messageStorage); // 지금까지 쌓인 모든 메시지 반환
        return resultMap;
    }
	
	/*
	 01.spring 기동시 groupId가 새로 생성되고 kafka가 떠있으면 모든 메시지를 messageStorage담는다.
	 02.클라이언트에서 soket통신에서 
	 * */
	/* 로그 햇갈려서 일시 주석
	@KafkaListener(
		topics = "my-test-topic", 
		groupId = "test-group-#{T(java.util.UUID).randomUUID().toString()}", //그룹 ID를 바꾸면 처음부터 읽어옵니다. - springboot 재기동시 새로운 그룹 생성
		properties = {"auto.offset.reset=earliest"} // 처음부터 다 읽어라!
	)
	public void consume(Map<String, Object> message) {
		// 카프카에서 읽어온 데이터를 리스트에 저장 (과거 데이터 포함)
		messageStorage.add(message);
		System.out.println("카프카에서 받음: " + message);
		//웹소켓으로 실시간 전송
		messagingTemplate.convertAndSend("/topic/kafka-data", message);// registry.enableSimpleBroker("/topic") 연결
	}
	*/
}
