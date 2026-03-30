package com.reactapi.api.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class TestGridController {
	
	private static final String BASIC_GRID_LIST_REQUEST = "/test/getBasicGridList";
	private static final String PAGE_GRID_LIST_REQUEST = "/test/getPageGridList";
	
	@PostMapping(BASIC_GRID_LIST_REQUEST)
    public Map<String, Object> getBasicGridList(@RequestBody Map<String, Object> params) {
        Map<String, Object> resultMap = new HashMap<>();

        try {
        	ClassPathResource resource = new ClassPathResource("josn/sampleBoardMock.json");
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> dataList = mapper.readValue(resource.getInputStream(), new TypeReference<List<Map<String, Object>>>() {});

            resultMap.put("list", dataList);
            resultMap.put("totalCount", dataList.size());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return resultMap;
    }
	
	@PostMapping(PAGE_GRID_LIST_REQUEST)
    public Map<String, Object> getPageGridList(@RequestBody Map<String, Object> params) {
        Map<String, Object> resultMap = new HashMap<>();

        try {
            int page = params.get("page") != null ? (int) params.get("page") : 1;
            int size = params.get("size") != null ? (int) params.get("size") : 10;
            
            String searchTitle = (String) params.get("searchTitle");
            String searchWriter = (String) params.get("searchWriter");

            //샘플데이터 가져오기
            ClassPathResource resource = new ClassPathResource("josn/sampleBoardMock.json");
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> dataList = mapper.readValue(resource.getInputStream(), new TypeReference<List<Map<String, Object>>>() {});
            
            if (searchTitle != null && !searchTitle.isEmpty()) {
              dataList = dataList.stream()
                .filter(item -> item.get("title").toString().contains(searchTitle))
                .collect(Collectors.toList());
            }
            if (searchWriter != null && !searchWriter.isEmpty()) {
              dataList = dataList.stream()
                .filter(item -> item.get("writer").toString().contains(searchWriter))
                .collect(Collectors.toList());
            }
            
            
            
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
}
