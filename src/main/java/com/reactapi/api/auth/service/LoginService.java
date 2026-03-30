package com.reactapi.api.auth.service;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LoginService {
    public Map<String,Object> getUserById(String id) throws Exception {
    	Map<String,Object> result = null;
    	
    	
    	
    	
    	ClassPathResource resource = new ClassPathResource("josn/sampleUserMock.json");
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> dataList = mapper.readValue(resource.getInputStream(), new TypeReference<List<Map<String, Object>>>() {});
    	
    	for(Map<String, Object> data : dataList) {
    		if(data.get("id").equals(id)) {
    			String tmpPw = data.get("pw").toString();
    			//pw평문이라 오류방지용 암호화
    			BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    			String encodedPw = encoder.encode(tmpPw);
    			 data.put("pw",encodedPw);
    			result = data;
    			break;
    		}
    	}
    	return result;
    	
    }
}
