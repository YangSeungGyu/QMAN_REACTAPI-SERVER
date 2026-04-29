package com.reactapi.api.auth;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.ExampleObject;


@Tag(name = "02.로그인권한 테스트", description = "/auth/")
@RestController
public class AuthController {
	
	/**
	 * 
	 * @param params
	 * @return
	 */
	@Operation(summary = "로그인 후 테스트")
	@PostMapping("/auth/test01")
    public Map<String, Object> test01(
    		@io.swagger.v3.oas.annotations.parameters.RequestBody(
    				description = "Map<String, Object> params",
    				content = @Content(
    						schema = @Schema(implementation = Map.class),
    						examples = @ExampleObject(
    								value = "{  }"
    						)
    				)
    		)
    		@RequestBody Map<String, Object> params) {
		Map<String, Object> resultMap = new HashMap<String, Object>();
		try {
			 resultMap.put("test", "111test");
		}catch(Exception e) {
			e.printStackTrace();
		}
        return resultMap;
    }
}
