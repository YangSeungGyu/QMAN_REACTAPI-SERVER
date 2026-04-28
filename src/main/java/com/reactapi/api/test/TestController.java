package com.reactapi.api.test;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
	
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
}
