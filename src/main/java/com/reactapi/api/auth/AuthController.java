package com.reactapi.api.auth;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
	
	/**
	 * 
	 * @param params
	 * @return
	 */
	@PostMapping("/auth/test01")
    public Map<String, Object> test01(@RequestBody Map<String, Object> params) {
		Map<String, Object> resultMap = new HashMap<String, Object>();
		try {
			 resultMap.put("test", "111test");
		}catch(Exception e) {
			e.printStackTrace();
		}
        return resultMap;
    }
}
