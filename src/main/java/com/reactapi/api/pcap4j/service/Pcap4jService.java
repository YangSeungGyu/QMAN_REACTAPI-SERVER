package com.reactapi.api.pcap4j.service;

import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.TcpPacket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
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
    private String webIp; 
    
    @Value("${web.port}")
    private String webPort;

    private Set<String> localIps = new HashSet<>();
    private static final Pattern IP_PATTERN = Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");
    private final SimpMessagingTemplate messagingTemplate;
    private Thread captureThread;
    private boolean isCapturing = false;

    public Pcap4jService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() {
        detectLocalIps();
        System.out.println("[Pcap4j] 외부 공인 IP (web.ip): " + webIp);
        System.out.println("[Pcap4j] 감지된 로컬 IP 목록: " + localIps);
        startCapture();
    }

    private void detectLocalIps() {
        localIps.add(webIp); 
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            if (nics == null) return;

            for (NetworkInterface nic : Collections.list(nics)) {
                if (!nic.isUp()) continue;
                for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                    String ip = addr.getHostAddress();
                    if (addr.isLoopbackAddress()) continue;
                    if (ip.contains(":")) continue; 

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
                if (allDevs.isEmpty()) {
                    System.out.println("[Pcap4j] 랜카드를 찾을 수 없습니다.");
                    return;
                }

                List<Thread> ifThreads = new ArrayList<>();

                for (PcapNetworkInterface nif : allDevs) {
                    Thread ifThread = new Thread(() -> {
                        try {
                            // 버퍼 크기를 65536으로 넉넉하게 유지
                            PcapHandle handle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10);
                            System.out.println("[Pcap4j] 감시 시작 -> " + nif.getDescription());
                            
                            String nifName = nif.getName(); 

                            while (isCapturing && !Thread.currentThread().isInterrupted()) {
                                if("any".equals(nifName)) continue; 
                                Packet packet = handle.getNextPacket();

                                if (packet != null) {
                                    // 중요: 패킷 구조를 쪼개서 매핑 객체 가져오기
                                    Map<String, String> parsed = parsePacketStructure(packet);
                                    if (parsed == null) continue; // IP 패킷이 아니면 패스

                                    String direction = parsed.get("direction");
                                    if ("[기타]".equals(direction)) continue;

                                    String proto = parsed.getOrDefault("Protocol", "");
                                    if ("IGMP".equals(proto)) continue;
                                    
                                    String destinationPort = parsed.get("Destination port");
                                    String sourcePort = parsed.get("Source port");
                                    String textData = parsed.get("Text data"); // 발라낸 순수 데이터

                                    // 필터링 로직 규칙 유지
                                    if ("[보내는 패킷]".equals(direction)) {
                                        if (sourcePort != null && sourcePort.contains("8199")) {
                                            if (textData == null || textData.isBlank()) {
                                                continue;
                                            }
                                        }
                                    } else if ("[받은 패킷]".equals(direction)) {
                                        if (destinationPort != null && destinationPort.contains("8199")) {
                                            if (textData == null || textData.isBlank()) {
                                                continue;
                                            }
                                        }
                                    }
                                  
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("nifName(랜카드) : ").append(nifName).append("\n");
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

                    ifThread.setDaemon(true); 
                    ifThreads.add(ifThread);
                    ifThread.start();
                }

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

    /**
     * 흔들리는 문자열 파싱 대신, 패킷 객체 모델을 직접 분석하는 안전한 메서드
     */
    private Map<String, String> parsePacketStructure(Packet packet) {
        // IPv4 레이어 추출
        IpV4Packet ipV4Packet = packet.get(IpV4Packet.class);
        if (ipV4Packet == null) return null;

        Map<String, String> info = new LinkedHashMap<>();
        
        // IP 및 프로토콜 추출
        String srcIp = ipV4Packet.getHeader().getSrcAddr().getHostAddress();
        String dstIp = ipV4Packet.getHeader().getDstAddr().getHostAddress();
        info.put("Protocol", ipV4Packet.getHeader().getProtocol().name());
        info.put("Source address", srcIp);
        info.put("Destination address", dstIp);

        // TCP 레이어 추출 (포트 및 페이로드 데이터용)
        TcpPacket tcpPacket = packet.get(TcpPacket.class);
        if (tcpPacket != null) {
            info.put("Source port", String.valueOf(tcpPacket.getHeader().getSrcPort().valueAsInt()));
            info.put("Destination port", String.valueOf(tcpPacket.getHeader().getDstPort().valueAsInt()));
            
            // ★ 핵심: 헤더 제외한 실제 알맹이 데이터(Payload) 바이트 배열 가져오기
            Packet payload = tcpPacket.getPayload();
            if (payload != null) {
                byte[] rawPayload = payload.getRawData();
                // 깨진 바이너리가 섞여있을 수 있으므로 문자열로 안전하게 디코딩
                String convertedText = new String(rawPayload, StandardCharsets.UTF_8).trim();
                info.put("Text data", convertedText);
            } else {
                info.put("Text data", "");
            }
        } else {
            // TCP가 아닐 경우 기존처럼 데이터가 있으면 변환 시도
            byte[] rawData = packet.getRawData();
            if (rawData != null) {
                info.put("Text data", new String(rawData, StandardCharsets.UTF_8).trim());
            }
        }

        // 방향성 판단
        String direction = "[기타]";
        if (localIps.contains(srcIp)) {
            direction = "[보내는 패킷]";
        } else if (localIps.contains(dstIp)) {
            direction = "[받은 패킷]";
        }

        Map<String, String> result = new LinkedHashMap<>();
        result.put("direction", direction);
        result.putAll(info);

        return result;
    }
}