package com.reactapi.api.test.member;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reactapi.api.auth.service.LoginService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.ExampleObject;


@Tag(name = "01.회원가입", description = "/member/")
@RestController
public class MemberController {
	
	
	@Autowired
	private LoginService loginService;

	
	/**
	 * 
	 * @param params
	 * @return resultMap
	 */
	@Operation(summary = "개인정보 조회")
	@PostMapping("/member/checkMobileAuth")
    public Map<String, Object> checkMobileAuth(
    		@io.swagger.v3.oas.annotations.parameters.RequestBody(
    				description = "Map<String, Object> params",
    				content = @Content(
    						schema = @Schema(implementation = Map.class),
    						examples = @ExampleObject(
    								value = "{ \"name\": \"홍길동\", \"phone\": \"0101111111\", \"ssnFront\": \"860101\", \"ssnBackFirst\": \"1\" }"
    						)
    				)
    		)
    		
    		@RequestBody Map<String, Object> params) {
		Map<String, Object> resultMap = new HashMap<String, Object>();
		try {
		
		    String name = params.get("name").toString();
			String phone = params.get("phone").toString();
			String formattedPhone = phone.replaceAll("-", "");
			String ssnFront = params.get("ssnFront").toString();
			String ssnBackFirst = params.get("ssnBackFirst").toString();
			
			
			//인증 가능한 개인정보 샘플데이터 가져오기
            ClassPathResource resource = new ClassPathResource("josn/sampleMobileAuthMock.json");
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> dataList = mapper.readValue(resource.getInputStream(), new TypeReference<List<Map<String, Object>>>() {});
			
			
			boolean isSuccess = false;
			String foundCi = null;

	        // 3. 루프 돌며 비교
	        for (Map<String, Object> user : dataList) {
	            if (user.get("name").equals(name) &&
	            	user.get("phone").equals(formattedPhone) && 
	                user.get("ssnFront").equals(ssnFront) && 
	                user.get("ssnBackFirst").equals(ssnBackFirst)) {
	            	isSuccess = true;
	                foundCi = user.get("ci").toString();
	                break;
	            }
	        }
	        resultMap.put("isSuccess", isSuccess);
	        if(isSuccess) {
		        resultMap.put("ci", foundCi);
		        resultMap.put("message", "성공");
	        }else {
		        resultMap.put("ci", null);
		        resultMap.put("message", "일치하는 사용자 정보가 없습니다.");
	        }
		}catch(Exception e) {
			e.printStackTrace();
		}
        return resultMap;
    }
	
	
	/**
	 * 
	 * @param params
	 * @return resultMap
	 */
	@Operation(summary = "개인정보 조회")
	@PostMapping("/member/checkUserId")
    public Map<String, Object> checkUserId(
    		@io.swagger.v3.oas.annotations.parameters.RequestBody(
    				description = "Map<String, Object> params",
    				content = @Content(
    						schema = @Schema(implementation = Map.class),
    						examples = @ExampleObject(
    								value = "{ \"userId\": \"test\" }"
    						)
    				)
    		)
    		@RequestBody Map<String, Object> params) {
		Map<String, Object> resultMap = new HashMap<String, Object>();
		try {
			boolean isDupl = false;
			 String id = params.get("userId").toString();
			 Map<String,Object> user = loginService.getUserById(id);
			 if(user != null) {
				 isDupl = true;
			 }
			 resultMap.put("isDupl", isDupl);
			
		}catch(Exception e) {
			e.printStackTrace();
		}
        return resultMap;
    }

}
