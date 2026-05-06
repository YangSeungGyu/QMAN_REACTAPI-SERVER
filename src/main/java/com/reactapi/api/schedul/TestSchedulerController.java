package com.reactapi.api.schedul;


import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "05.스케줄러", description = "/scheduler/")
@RequestMapping("/scheduler")
@RestController
public class TestSchedulerController {
	//DB대신 전역 변수 사용
	public static boolean TEST_SCHEDUL_STATUS_01 = false;
	public static boolean TEST_SCHEDUL_STATUS_02 = false;

	
	@PostMapping("/changeStatus")
    public Map<String, Object> changeStatus(@RequestBody Map<String, Object> params) {
		 Map<String, Object> resultMap = new HashMap<>();
		 resultMap.put("success",false);
		try {
			String schedulId = params.get("schedulId").toString();
			String status = params.get("status").toString();
			
			if("01".equals(schedulId)) {
				if("Y".equals(status)) {
					this.TEST_SCHEDUL_STATUS_01 = true;
				}else {
					this.TEST_SCHEDUL_STATUS_01 = false;
				}
			} else if("02".equals(schedulId)) {
				if("Y".equals(status)) {
					this.TEST_SCHEDUL_STATUS_02 = true;
				}else {
					this.TEST_SCHEDUL_STATUS_02 = false;
				}
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		resultMap.put("success",true);
        return resultMap;
    }
	
	
	@PostMapping("/getStatus")
    public Map<String, Object> getStatus() {
		 Map<String, Object> resultMap = new HashMap<>();
		try {
			 resultMap.put("01",(this.TEST_SCHEDUL_STATUS_01 ? "Y" : "N"));
			 resultMap.put("02",(this.TEST_SCHEDUL_STATUS_02 ? "Y" : "N"));
		}catch(Exception e) {
			e.printStackTrace();
		}
        return resultMap;
    }
	
}
