package com.reactapi.api.common;

import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
public class CommonController {
	
	@PostMapping("/common/getCommonData")
    public Map<String, Object> getCommonData() {
        Map<String, Object> resultMap = new HashMap<>();
        try { 
        	Map<String, String> com01Map = new HashMap<>();
        	com01Map.put("aaa", "AAA");
        	com01Map.put("bbb", "BBB");
        	com01Map.put("ccc", "CCC");
        	
        	Map<String, String> com02Map = new HashMap<>();
        	com02Map.put("일", "111");
        	com02Map.put("이", "222");
        	com02Map.put("삼", "333");
        	
        	resultMap.put("COM01", com01Map);
        	resultMap.put("COM02", com02Map);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return resultMap;
    }
		
}