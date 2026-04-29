package com.reactapi.api.kafka;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Hidden;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.kafka.support.KafkaHeaders;
import java.util.concurrent.TimeUnit;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;

@Hidden
@RestController
public class ExcelUploadController implements ConsumerSeekAware{
	
	private final Logger log = LoggerFactory.getLogger(getClass());
	
	@Autowired
	private AdminClient adminClient;
	
	@Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
	
	   //웹소캣 전용
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
	
	public static final String KAFKA_EXCEL_UPLOAD = "/kafka/excelUpload";
	public static final String KAFKA_EXCEL_GET_ALL = "/kafka/excelGetAll";
	public static final String KAFKA_EXCEL_FILE_LIST = "/kafka/excelFileList";
	public static final String KAFKA_EXCEL_FILE_DOWNLOAD = "/kafka/excelFileDownload";
	public static final String KAFKA_TOPIC_CREATE = "/kafka/topicCreate";
	
	//중복막기
	private final Set<String> processedKeys = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	
	//kafka 데이터 보관
	//private final List<Map<String, Object>> messageStorage = new CopyOnWriteArrayList<>(); 이건 전부 다 읽어오는거.
	private final Map<String, Map<String, Object>> messageStorage = new ConcurrentHashMap<>();
	
	@PostMapping(KAFKA_TOPIC_CREATE)
	public ResponseEntity<?> topicCreate(@RequestBody Map<String, String> request) {
	    try {
	        String id = request.get("id");
	        if (id == null || id.isEmpty()) {
	            return ResponseEntity.badRequest().body("id가 없습니다.");
	        }

	        String topicReqName = "chillerCOP-Req_" + id;
	        String topicResName = "chillerCOP-Res_" + id;

	        // 기존 토픽 목록 조회
	        Set<String> existingTopics = adminClient.listTopics().names().get();
	        
	        if (existingTopics.contains(topicReqName) && existingTopics.contains(topicResName)) {
	            log.info("토픽 이미 존재: {}", topicReqName);
	            Map<String, Object> resultMap = new HashMap<>();
	            resultMap.put("result", "success");
	            resultMap.put("created", false);
	            return ResponseEntity.ok(resultMap);
	        }

	        // 없으면 생성
	        NewTopic newReqTopic = new NewTopic(topicReqName, 1, (short) 1);
	        NewTopic newResTopic = new NewTopic(topicResName, 1, (short) 1);
	        
	        adminClient.createTopics(Collections.singleton(newReqTopic)).all().get();
	        adminClient.createTopics(Collections.singleton(newResTopic)).all().get();
	        log.info("토픽 생성 완료: {}", topicReqName);
	        log.info("토픽 생성 완료: {}", topicResName);

	        Map<String, Object> resultMap = new HashMap<>();
	        resultMap.put("result", "success");
	        resultMap.put("created", true);
	        return ResponseEntity.ok(resultMap);

	    } catch (Exception e) {
	        e.printStackTrace();
	        return ResponseEntity.status(500).body("토픽 생성 중 에러: " + e.getMessage());
	    }
	}
	
	@PostMapping(KAFKA_EXCEL_UPLOAD)
	public ResponseEntity<?> uploadExcel(@RequestParam("file") MultipartFile file,@RequestParam("id") String id) {
		try {
		    //01.빈파일 검증
		    if (file.isEmpty()) {
		        return ResponseEntity.badRequest().body("파일이 비어있습니다.");
		    } else if(id.isEmpty()) {
		        return ResponseEntity.badRequest().body("해당유저에 id이 없습니다");
		    }

		    byte[] fileBytes = file.getBytes();

		    // 02. 파일 떨구기
		    String uploadDir = "C:/stEMS/uploads/"+id+"/";
		    File dir = new File(uploadDir);
		    if (!dir.exists()) dir.mkdirs();
		    String savedPath = uploadDir + System.currentTimeMillis() + "_" + file.getOriginalFilename();
		    try (FileOutputStream fos = new FileOutputStream(savedPath)) {
		        fos.write(fileBytes);
		    }
		    log.info("파일 저장 완료: {}", savedPath);

		    //03.엑셀데이터 json타입으로 변경
		    List<Map<String, Object>> excelDataList = getExcelData(fileBytes, id);
		    if(excelDataList == null) {
		        return ResponseEntity.badRequest().body("엑셀에 데이터가 없습니다.");
		    }
		    log.info("변환된 JSON 데이터: " + excelDataList);

		    //04.카프카 전송
		    Map<String, Object> kafkaSendMap = new HashMap<>();
		    kafkaSendMap.put("ID", id);
		    kafkaSendMap.put("data", excelDataList);

		    try {
		        kafkaTemplate.send("chillerCOP-Req_" + id, kafkaSendMap).get(3, TimeUnit.SECONDS);
		    } catch (org.springframework.kafka.KafkaException e) {  // ← 이게 핵심! 추가
		        log.error("Kafka 전송 실패 (KafkaException): {}", e.getMessage());
		        return ResponseEntity.status(503).body("Kafka 토픽이 준비되지 않았습니다. 관리자에게 문의하세요.");
		    }

		    return ResponseEntity.ok(excelDataList);

		} catch (Exception e) {
		    e.printStackTrace();
		    return ResponseEntity.status(500).body("엑셀 처리 중 에러: " + e.getMessage());
		}
     
    }
	
	
	private List<Map<String, Object>> getExcelData(byte[] fileBytes,String id){
		List<Map<String, Object>> resultList = new ArrayList<>();
		DataFormatter formatter = new DataFormatter();
		
		// 1. XSSFWorkbook은 .xlsx 파일을 처리할 때 사용합니다.
        try (InputStream is = new ByteArrayInputStream(fileBytes);
             XSSFWorkbook workbook = new XSSFWorkbook(is)) {

            // 2. 첫 번째 시트 가져오기
            Sheet sheet = workbook.getSheetAt(0);
            
            // 3. 헤더 행(첫 번째 줄) 읽기 - JSON의 Key가 됩니다.
            Row headerRow = sheet.getRow(1);//두번째부터가 키값
            if (headerRow == null) {
                throw new Exception("엑셀에 데이터가 없습니다.");
            }
            
            List<String> customHeaderNames = Arrays.asList(
            	    "시간"
            		, "설비명", "냉수입구온도", "냉수출구온도", "냉수유량", "소비전력"
            );

            // 4. 데이터 행 반복 (두 번째 줄부터 끝까지)
            int titleRowCnt = 2;
            for (int i = titleRowCnt; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Map<String, Object> rowData = new HashMap<>();
                rowData.put("ID",id);//첫번째에 워크그룹 셋팅
                for (int j = 0; j < customHeaderNames.size(); j++) {
                    Cell cell = row.getCell(j);
                    String columnName = customHeaderNames.get(j);
                    
                    // 셀의 타입에 맞춰 안전하게 값 가져오기
                    String cellValue = formatter.formatCellValue(cell);
                    rowData.put(columnName, cellValue);
                }
                resultList.add(rowData);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
		return resultList;
	}
	
	/*
	 * 설명 : groupId가 새로발급되면 해당 topics으로 저장된 값을 모두 읽어옴.
	 * 		단 리스너라 기동할때만 읽어오고 저장하는건 workGroup당 하나만 저장됨.
	 * 		기동시 부하가 좀 있고 기동후에는 원활함.   
	 * */
	@KafkaListener(
		topicPattern = "chillerCOP-Req_.*",
	    groupId = "ems-excel-group-#{T(java.util.UUID).randomUUID().toString()}",
	    properties = {"auto.offset.reset=earliest","metadata.max.age.ms=10000"}
	)
	public void consumeExcelData(Map<String, Object> message,@Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
		log.warn("KAFKA - 전체 수신 topic:{},  message:{}",topic,message);
		String id = topic.substring(topic.lastIndexOf("_") + 1);
	    //String workGroup = String.valueOf(message.get("workGroup"));

	    List<Map<String, Object>> dataList = (List<Map<String, Object>>) message.get("data");
	    if (dataList == null || dataList.isEmpty()) {
	        log.warn("dataList가 비어있음 : {}", id);
	        return;
	    }

	    // id별 message 전체(dataList 포함) 덮어씌우기
	    messageStorage.put(id, message);

	    // 소켓으로 message 전체 전송 (프론트에서 receivedData.dataList로 꺼냄)
	    messagingTemplate.convertAndSend("/topic/chillerCOP-Req_"+id, message);
	}
	
	
	
	//엑셀로 KAFKA에 올린 전체 데이터 가져오기
	@PostMapping(KAFKA_EXCEL_GET_ALL)
    public Map<String, Object> KAFKA_EXCEL_GET_ALL() {
		log.debug("url : {}",KAFKA_EXCEL_GET_ALL);
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("result", "success");
        resultMap.put("data", messageStorage); // 지금까지 쌓인 모든 메시지 반환
        return resultMap;
    }
	
	//엑셀 파일 다운로드
	@PostMapping(KAFKA_EXCEL_FILE_LIST)
	public ResponseEntity<?> excelFileList(@RequestBody Map<String, String> params) {
	    try {
	    	String id = params.get("id");
	        String uploadDir = "C:/stEMS/uploads/"+id+"/";
	        File dir = new File(uploadDir);

	        if (!dir.exists() || !dir.isDirectory()) {
	        	Map<String, Object> resultMap = new HashMap<>();
	            resultMap.put("result", "success");
	            resultMap.put("data", new ArrayList<>());
	            return ResponseEntity.ok(resultMap);
	        }

	        File[] files = dir.listFiles((d, name) -> 
	            name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".csv")
	        );

	        List<Map<String, Object>> fileList = new ArrayList<>();
	        if (files != null) {
	            for (File file : files) {
	                Map<String, Object> fileInfo = new HashMap<>();
	                fileInfo.put("fileName", file.getName());
	                fileInfo.put("fileSize", file.length());
	                fileInfo.put("lastModified", new java.util.Date(file.lastModified()));
	                fileList.add(fileInfo);
	            }
	        }

	        // 최신순 정렬
	        fileList.sort((a, b) -> ((java.util.Date) b.get("lastModified"))
	                .compareTo((java.util.Date) a.get("lastModified")));

	        Map<String, Object> resultMap = new HashMap<>();
	        resultMap.put("result", "success");
	        resultMap.put("data", fileList);
	        return ResponseEntity.ok(resultMap);

	    } catch (Exception e) {
	        e.printStackTrace();
	        return ResponseEntity.status(500).body("파일 목록 조회 중 에러: " + e.getMessage());
	    }
	}
	
	//파일 다운로드
	@PostMapping(KAFKA_EXCEL_FILE_DOWNLOAD)
	public ResponseEntity<?> excelFileDownload(@RequestBody Map<String, String> request) {
	    try {
	        String fileName = request.get("fileName");
	        String id = request.get("id");
	        if (fileName == null || fileName.isEmpty()) {
	            return ResponseEntity.badRequest().body("파일명이 없습니다.");
	        }
	        
	        // 경로 traversal 방지
	        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
	            return ResponseEntity.badRequest().body("잘못된 파일명입니다.");
	        }
	        
	        String uploadDir = "C:/stEMS/uploads/" + id + "/";
	        File file = new File(uploadDir + fileName);
	        
	        if (!file.exists()) {
	            return ResponseEntity.status(404).body("파일을 찾을 수 없습니다: " + fileName);
	        }
	        
	        byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
	        
	        return ResponseEntity.ok()
	                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
	                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	                .body(fileBytes);
	                
	    } catch (Exception e) {
	        e.printStackTrace();
	        return ResponseEntity.status(500).body("파일 다운로드 중 에러: " + e.getMessage());
	    }
	}
}
