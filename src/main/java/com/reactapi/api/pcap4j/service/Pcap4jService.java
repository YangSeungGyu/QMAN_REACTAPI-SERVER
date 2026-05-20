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

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class Pcap4jService {

    @Value("${web.ip}")
    private String webIp; // 외부 공인 IP (다른 곳에서 사용 중이므로 그대로 유지)
    
    @Value("${web.port}")
    private String webPort;

    // 이 서버의 실제 로컬 IP 목록 (자동 감지) - NAT 환경 대응
    private Set<String> localIps = new HashSet<>();

    // IP 주소 추출용 정규식 (OS 무관하게 동작)
    private static final Pattern IP_PATTERN =
            Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");

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
        // 로컬 IP 자동 감지 (NIC에서 직접 읽어옴)
        detectLocalIps();
        System.out.println("[Pcap4j] 외부 공인 IP (web.ip): " + webIp);
        System.out.println("[Pcap4j] 감지된 로컬 IP 목록: " + localIps);
        startCapture();
    }

    /**
     * 이 서버에 할당된 모든 로컬 IP를 자동으로 감지
     * - 루프백(127.x.x.x)은 제외
     * - NAT 환경에서도 NIC에 붙은 실제 사설 IP를 정확히 가져옴
     * - web.ip(공인 IP)는 건드리지 않음
     */
    private void detectLocalIps() {
        localIps.add(webIp); // 공인 IP도 포함 (Windows 직접 연결 환경 대응)
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            if (nics == null) return;

            for (NetworkInterface nic : Collections.list(nics)) {
                // 비활성 NIC 스킵
                if (!nic.isUp()) continue;

                for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                    String ip = addr.getHostAddress();

                    // IPv6, 루프백 제외
                    if (addr.isLoopbackAddress()) continue;
                    if (ip.contains(":")) continue; // IPv6

                    localIps.add(ip);
                    System.out.println("[Pcap4j] 로컬 IP 발견: " + ip + " (" + nic.getDisplayName() + ")");
                }
            }
        } catch (Exception e) {
            System.out.println("[Pcap4j] 로컬 IP 감지 실패, web.ip 단독 사용: " + e.getMessage());
        }
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
                            
                            
                            String nifName = nif.getName(); //랜카드 장치명

                            while (isCapturing && !Thread.currentThread().isInterrupted()) {
                            	if("any".equals(nifName)) continue ; //리눅스 가상 랜카드 -중복데이터
                                Packet packet = handle.getNextPacket();

                                if (packet != null) {
                                    String packetContent = packet.toString();

                                    // IPv4 Header 없으면 스킵
                                    if (!packetContent.contains("[IPv4 Header")) continue;

                                    Map<String, String> parsed = parsePacket(packetContent);

                                    // 기타(local간 통신 / 브로드캐스트 등)는 전송 안 함
                                    
                                    String direction = parsed.get("direction");
                                    if ("[기타]".equals(direction)) continue;

                                    String proto = parsed.getOrDefault("Protocol", "");
                                    if (proto.equals("IGMP")) continue;
                                    
                                    //소캣통신 제외
                                    String HexStream = parsed.get("Hex stream");
                                    String destinationPort = parsed.get("Destination port");
                                    String sourcePort = parsed.get("Source port");
                                    if ("[보내는 패킷]".equals(direction)) {
                                    	if (sourcePort != null && sourcePort.contains("8199")) {
                                    		if (HexStream == null || HexStream.startsWith("c1 ")) {
                                                continue;
                                            }
                                    	}
                                    } else if ("[받은 패킷]".equals(direction)) {
                                        // 2. 8199 포트로 들어오는 패킷 중 '소켓 쓰레기 데이터'만 골라내기
                                        if (destinationPort != null && destinationPort.contains("8199")) {
                                        	//if (HexStream == null || HexStream.trim().isEmpty() || HexStream.startsWith("00 ")) {
                                            if (HexStream == null || HexStream.startsWith("00 ")) {
                                                continue;
                                            }
                                        }
                                    }
                                    
                                 
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("nifName(랜카드) : "+nifName+"\n");
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

    // 패킷 파싱 유틸 메서드
    private Map<String, String> parsePacket(String packetContent) {
        Map<String, String> info = new LinkedHashMap<>();
        String[] lines = packetContent.split("\\r?\\n");

        boolean inIpv4Header = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // IPv4 Header 섹션 진입/탈출 감지
            if (trimmed.startsWith("[IPv4 Header")) {
                inIpv4Header = true;
            } else if (trimmed.startsWith("[") && !trimmed.startsWith("[IPv4")) {
                inIpv4Header = false;
            }

            if (inIpv4Header) {
                // Protocol 파싱
                if (trimmed.startsWith("Protocol:")) {
                    String proto = trimmed.replaceAll(".*\\((.+?)\\).*", "$1");
                    if (proto.equals(trimmed)) {
                        proto = trimmed.replace("Protocol:", "").trim();
                    }
                    info.put("Protocol", proto);
                }

                // Source address 파싱 - 정규식으로 IP 직접 추출 (OS 무관)
                if (trimmed.startsWith("Source address:")) {
                    String ip = extractIp(trimmed);
                    if (ip != null) info.putIfAbsent("Source address", ip);
                }

                // Destination address 파싱 - 정규식으로 IP 직접 추출 (OS 무관)
                if (trimmed.startsWith("Destination address:")) {
                    String ip = extractIp(trimmed);
                    if (ip != null) info.putIfAbsent("Destination address", ip);
                }
            }
            
            // Source port 파싱 (헤더 섹션 무관)
            if (trimmed.startsWith("Source port:")) {
                String port = trimmed.replaceAll("Source port: (\\d+).*", "$1");
                info.put("Source port", port);
            }

            // Destination port 파싱 (헤더 섹션 무관)
            if (trimmed.startsWith("Destination port:")) {
                String port = trimmed.replaceAll("Destination port: (\\d+).*", "$1");
                String portName = trimmed.contains("(")
                        ? trimmed.replaceAll(".*\\((.+?)\\).*", "$1") : "unknown";
                info.put("Destination port", port);
            }

            // Hex stream 파싱
            if (trimmed.startsWith("Hex stream:")) {
                info.put("Hex stream", trimmed.replace("Hex stream:", "").trim());
            }
        }

        // 방향 판단 - localIps(자동 감지된 모든 IP) 기준으로 비교
        String src = info.getOrDefault("Source address", "");
        String dst = info.getOrDefault("Destination address", "");

        String direction;
        if (localIps.contains(src)) {
            direction = "[보내는 패킷]";
        } else if (localIps.contains(dst)) {
            direction = "[받은 패킷]";
        } else {
            direction = "[기타]";
        }

        // direction을 맨 앞에 오도록 새 Map에 담기
        Map<String, String> result = new LinkedHashMap<>();
        result.put("direction", direction);
        result.putAll(info);

        return result;
    }

    /**
     * 문자열에서 IPv4 주소를 정규식으로 추출
     * Windows(npcap), Linux(libpcap) 양쪽 포맷 대응
     * 예) "Source address: /192.168.0.1"  → "192.168.0.1"
     *     "Source address: 192.168.0.1"   → "192.168.0.1"
     *     "Source address: /hostname.local" → null (IP 없으면 null)
     */
    private String extractIp(String line) {
        Matcher m = IP_PATTERN.matcher(line);
        return m.find() ? m.group(1) : null;
    }
}
