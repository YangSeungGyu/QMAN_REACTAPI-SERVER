package com.reactapi.api.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

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
    
    
    @PostMapping("/test/uploadExcel")
    public List<Map<String, Object>> uploadExcel(@RequestParam("file") MultipartFile file) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            //헤더
            List<String> headerList = Arrays.asList("time", "name", "testNum");
            
            // 2. 헤더 읽기 (2번째 줄 = index 1)
            Row headerRow = sheet.getRow(1); 
            if (headerRow == null) {
                throw new RuntimeException("엑셀 양식의 헤더(2번째 줄)가 비어있습니다.");
            }

            // 3. 헤더 검증
            for (int i = 0; i < headerList.size(); i++) {
                Cell cell = headerRow.getCell(i);
                String excelHeader = formatter.formatCellValue(cell).trim();
                String expectedHeader = headerList.get(i);

                if (!expectedHeader.equals(excelHeader)) {
                    System.err.println("검증 실패: " + (i+1) + "번째 컬럼이 '" + expectedHeader + "'가 아닙니다. (입력값: " + excelHeader + ")");
                    return resultList; // 검증 실패 시 빈 리스트 반환 (혹은 에러 응답)
                }
            }
            
            System.out.println("헤더 검증 완료: " + headerList);

            // 4. 데이터 읽기 (3번째 줄부터 = index 2)
            int lastRowNum = sheet.getLastRowNum();
            for (int rowIndex = 2; rowIndex <= lastRowNum; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                Map<String, Object> rowData = new LinkedHashMap<>();
                boolean hasData = false;

                for (int i = 0; i < headerList.size(); i++) {
                    Cell cell = row.getCell(i);
                    String value = formatter.formatCellValue(cell).trim();
                    
                    // 하드코딩된 컬럼명을 Key로 사용
                    rowData.put(headerList.get(i), value);
                    if (!value.isEmpty()) hasData = true;
                }

                if (hasData) {
                    System.out.println("[Excel Data Log] " + rowData);
                    resultList.add(rowData);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return resultList;
    }
}
