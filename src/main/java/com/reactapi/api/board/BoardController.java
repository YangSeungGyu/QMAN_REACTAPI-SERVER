package com.reactapi.api.board;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Tag(name = "03.게시판 관리", description = "/board/")
@RestController
public class BoardController {

	@Operation(summary = "게시판 목록")
	@PostMapping("/board/getBoardList")
    public Map<String, Object> getBoardList(
    		@io.swagger.v3.oas.annotations.parameters.RequestBody(
    				description = "Map<String, Object> params",
    				content = @Content(
    						schema = @Schema(implementation = Map.class),
    						examples = @ExampleObject(
    								value = "{ \"page\": 1, \"size\": 10 }"
    						)
    				)
    		)
    		
    		@RequestBody Map<String, Object> params) {
        Map<String, Object> resultMap = new HashMap<>();

        try {
            int page = params.get("page") != null ? (int) params.get("page") : 1;
            int size = params.get("size") != null ? (int) params.get("size") : 10;

            //샘플데이터 가져오기
            ClassPathResource resource = new ClassPathResource("josn/sampleBoardMock.json");
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> dataList = mapper.readValue(resource.getInputStream(), new TypeReference<List<Map<String, Object>>>() {});
            
            
            
            int totalCount = dataList.size();
            int start = (page - 1) * size;
            int end = Math.min(start + size, totalCount); // 리스트 크기를 넘지 않도록 방어

            List<Map<String, Object>> pagedList = new ArrayList<>();
            
            if (start < totalCount) {
                pagedList = dataList.subList(start, end);
            }

            resultMap.put("list", pagedList);
            resultMap.put("totalCount", totalCount);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return resultMap;
    }
	
	
	
	@Operation(summary = "게시판 상세")
	@PostMapping("/board/getBoardDetail")
    public Map<String, Object> getBoardDetail(
    		@io.swagger.v3.oas.annotations.parameters.RequestBody(
    		        description = "Map<String, Object> params",
    		        content = @Content(
    		            schema = @Schema(implementation = Map.class),
    		            examples = @ExampleObject(
    		                value = "{ \"idx\": 1}"
    		            )
    		        )
    		    )
    		@RequestBody Map<String, Object> params) {
        Map<String, Object> resultMap = new HashMap<>();

        try {
        	String idx =  params.get("idx").toString();

            //샘플데이터 가져오기
            
            ClassPathResource resource = new ClassPathResource("josn/sampleBoardMock.json");
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> dataList = mapper.readValue(resource.getInputStream(), new TypeReference<List<Map<String, Object>>>() {});
            
            resultMap = dataList.stream().filter(map->idx.equals(map.get("idx").toString()))
            			.findAny().orElse(null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return resultMap;
    }
	
}