package com.reactapi.api.pcap4j.service;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.Packet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class Pcap4jService {
	
	@Value("${web.ip}")
    private String webIp;
	
	// STOMP 브로커로 메시지를 보낼 수 있게 해주는 스프링 내장 템플릿
    private final SimpMessagingTemplate messagingTemplate;
    private Thread captureThread;
    private boolean isCapturing = false;

    // 생성자 주입
    public Pcap4jService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // @PostConstruct: 의존성 주입이 완료된 후, 스프링 부트가 켜지면서 자동으로 이 메서드를 실행함
    @PostConstruct
    public void init() {
        startCapture();
    }

    public synchronized void startCapture() {
        if (isCapturing) return;
        isCapturing = true;

        captureThread = new Thread(() -> {
            try {
                List<PcapNetworkInterface> allDevs = Pcaps.findAllDevs();
                System.out.println("====== [내 PC 진짜 랜카드 목록 확인] ======");
                for (int i = 0; i < allDevs.size(); i++) {
                    System.out.println("[" + i + "] " + allDevs.get(i).getDescription());
                }
                System.out.println("=========================================");

                if (allDevs.isEmpty()) {
                    System.out.println("[Pcap4j] 랜카드를 찾을 수 없습니다.");
                    return;
                }

                // 랜카드별로 각각 스레드 생성
                List<Thread> ifThreads = new ArrayList<>();

                for (PcapNetworkInterface nif : allDevs) {
                    Thread ifThread = new Thread(() -> {
                        try {
                            PcapHandle handle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10);
                            System.out.println("[Pcap4j] 감시 시작 -> " + nif.getDescription());

                            while (isCapturing && !Thread.currentThread().isInterrupted()) {
                                Packet packet = handle.getNextPacket();

                                if (packet != null) {
                                    String packetContent = packet.toString();

                                    // IPv4 Header 없으면 스킵
                                    if (!packetContent.contains("[IPv4 Header")) continue;

                                    Map<String, String> parsed = parsePacket(packetContent);
                                    
                                    //현재 패킷 soket통신 패킷마자 잡아버리기에 기타(local간 통신)은 빼버림
                                    if("[기타]".equals(parsed.get("direction"))) continue;

                                   // String dst = parsed.getOrDefault("Destination address", "");
                                    //if (dst.startsWith("192.168.")) continue;

                                    String proto = parsed.getOrDefault("Protocol", "");
                                    if (proto.equals("IGMP")) continue;

                                    StringBuilder sb = new StringBuilder();
                                    parsed.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));

                                    messagingTemplate.convertAndSend("/packet", sb.toString());
                                }
                                Thread.sleep(10);
                            }
                            handle.close();
                            System.out.println("[Pcap4j] 감시 종료 -> " + nif.getDescription());

                        } catch (Exception e) {
                            System.out.println("[Pcap4j] 에러 -> " + nif.getDescription() + " : " + e.getMessage());
                        }
                    });

                    ifThread.setDaemon(true); // 메인 종료 시 같이 종료
                    ifThreads.add(ifThread);
                    ifThread.start();
                }

                // 모든 랜카드 스레드가 끝날 때까지 대기
                for (Thread t : ifThreads) {
                    t.join();
                }

            } catch (Exception e) {
                System.out.println("[Pcap4j] 초기화 에러: " + e.getMessage());
            }
        });

        captureThread.start();
    }

    @PreDestroy
    public void stopCapture() {
        isCapturing = false;
        if (captureThread != null) {
            captureThread.interrupt();
        }
        System.out.println("[Pcap4j] 패킷 감시단이 종료되었습니다.");
    }
    
 // 패킷 파싱 유틸 메서드 추가
    private Map<String, String> parsePacket(String packetContent) {
        Map<String, String> info = new LinkedHashMap<>();
        String[] lines = packetContent.split("\\r?\\n");
        
        boolean inIpv4Header = false;
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            if (trimmed.startsWith("[IPv4 Header")) {
                inIpv4Header = true;
            } else if (trimmed.startsWith("[") && !trimmed.startsWith("[IPv4")) {
                inIpv4Header = false;
            }
            
            if (inIpv4Header && trimmed.startsWith("Protocol:")) {
                info.put("Protocol", trimmed.replaceAll(".*\\((.+?)\\).*", "$1"));
            }
            if (inIpv4Header && trimmed.startsWith("Source address: /")) {
                info.putIfAbsent("Source address", trimmed.replace("Source address: /", ""));
            }
            if (inIpv4Header && trimmed.startsWith("Destination address: /")) {
                info.putIfAbsent("Destination address", trimmed.replace("Destination address: /", ""));
            }
            if (trimmed.startsWith("Destination port:")) {
                String port = trimmed.replaceAll("Destination port: (\\d+).*", "$1");
                String portName = trimmed.contains("(") 
                    ? trimmed.replaceAll(".*\\((.+?)\\).*", "$1") : "unknown";
                info.put("Destination port", port + " (" + portName + ")");
            }
            if (trimmed.startsWith("Hex stream:")) {
                info.put("Hex stream", trimmed.replace("Hex stream:", "").trim());
            }
        }
        
        // 방향 판단 - LinkedHashMap이라 맨 앞에 못 넣으니 새 Map에 순서대로 담기
        String myIp = webIp;
        String src = info.getOrDefault("Source address", "");
        String dst = info.getOrDefault("Destination address", "");
        
        String direction;
        if (src.equals(myIp)) {
            direction = "[보내는 패킷]";
        } else if (dst.equals(myIp)) {
            direction = "[받은 패킷]";
        } else {
            direction = "[기타]"; // 브로드캐스트, 멀티캐스트 등
        }
        
        Map<String, String> result = new LinkedHashMap<>();
        result.put("direction", direction); // 최상단
        result.putAll(info);
        
        return result;
    }
    
}
