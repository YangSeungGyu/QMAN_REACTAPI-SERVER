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
                    if (ip.contains(":")) continue; // IPv6 제외

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
                            PcapHandle handle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10);
                            System.out.println("[Pcap4j] 감시 시작 -> " + nif.getDescription());

                            String nifName = nif.getName();

                            while (isCapturing && !Thread.currentThread().isInterrupted()) {
                                if ("any".equals(nifName)) continue; // 리눅스 가상 랜카드 - 중복데이터 방지

                                Packet packet = handle.getNextPacket();

                                if (packet != null) {
                                    Map<String, String> parsed = parsePacketStructure(packet);
                                    if (parsed == null) continue; // IP 패킷 아니면 패스

                                    String direction = parsed.get("direction");
                                    if ("[기타]".equals(direction)) continue;

                                    String proto = parsed.getOrDefault("Protocol", "");
                                    if ("IGMP".equals(proto)) continue;

                                    String destinationPort = parsed.get("Destination port");
                                    String sourcePort     = parsed.get("Source port");
                                    String textData       = parsed.get("Text data");   // 복호화된 텍스트
                                    String hexStream      = parsed.get("Hex stream");  // 소켓 판별용 raw hex

                                    // ★ 정상버전과 동일한 소켓 판별 로직 ★
                                    if ("[보내는 패킷]".equals(direction)) {
                                        if (sourcePort != null && sourcePort.contains("8199")) {
                                            // hex가 없거나 c1 으로 시작하면 소켓 쓰레기 데이터 → 제외
                                            if (hexStream == null || hexStream.startsWith("c1 ")) {
                                                continue;
                                            }
                                        }
                                    } else if ("[받은 패킷]".equals(direction)) {
                                        if (destinationPort != null && destinationPort.contains("8199")) {
                                            // hex가 없거나 00 으로 시작하면 소켓 쓰레기 데이터 → 제외
                                            if (hexStream == null || hexStream.startsWith("00 ")) {
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
     * 패킷 객체 모델을 직접 분석하는 안전한 메서드
     * - IpV4Packet / TcpPacket 구조체에서 필드 직접 추출 (OS 무관, 문자열 파싱 불필요)
     * - Hex stream 을 별도로 생성하여 소켓 판별 로직에 사용
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

        // TCP 레이어 추출
        TcpPacket tcpPacket = packet.get(TcpPacket.class);
        if (tcpPacket != null) {
            info.put("Source port", String.valueOf(tcpPacket.getHeader().getSrcPort().valueAsInt()));
            info.put("Destination port", String.valueOf(tcpPacket.getHeader().getDstPort().valueAsInt()));

            Packet payload = tcpPacket.getPayload();
            if (payload != null) {
                byte[] rawPayload = payload.getRawData();

                // ★ 소켓 판별용 Hex stream 생성 (정상버전과 동일한 "xx xx xx ..." 형식)
                info.put("Hex stream", bytesToHexStream(rawPayload));

                // 복호화: hex 바이트를 UTF-8 텍스트로 변환
                String convertedText = new String(rawPayload, StandardCharsets.UTF_8).trim();
                info.put("Text data", convertedText);
            } else {
                info.put("Hex stream", null);
                info.put("Text data", "");
            }
        } else {
            // TCP가 아닐 경우 raw 데이터 처리
            byte[] rawData = packet.getRawData();
            if (rawData != null) {
                info.put("Hex stream", bytesToHexStream(rawData));
                info.put("Text data", new String(rawData, StandardCharsets.UTF_8).trim());
            } else {
                info.put("Hex stream", null);
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

    /**
     * byte[] → "xx xx xx ..." 형식의 hex 문자열 변환
     * 정상버전의 Hex stream 포맷과 동일하게 맞춤
     */
    private String bytesToHexStream(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            sb.append(String.format("%02x", bytes[i]));
            if (i < bytes.length - 1) sb.append(" ");
        }
        return sb.toString();
    }
}
