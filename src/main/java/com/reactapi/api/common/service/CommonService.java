package com.reactapi.api.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class CommonService {

    private enum OsType { WINDOWS, LINUX, UNSUPPORTED }

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ScheduledExecutorService scheduler;
    private OsType osType;

    public CommonService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            osType = OsType.WINDOWS;
        } else if (osName.contains("linux")) {
            osType = OsType.LINUX;
        } else {
            osType = OsType.UNSUPPORTED;
        }
        System.out.println("[Monitor] 감지된 OS: " + System.getProperty("os.name") + " → " + osType);
        startMemoryMonitoring();
        startStorageMonitoring();
    }

    // ──────────────────────────────────────────────
    // Memory
    // ──────────────────────────────────────────────

    public synchronized void startMemoryMonitoring() {
        if (scheduler != null && !scheduler.isShutdown()) return;

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> memoryMap = switch (osType) {
                    case LINUX       -> readLinuxMemory();
                    case WINDOWS     -> readWindowsMemory();
                    case UNSUPPORTED -> unsupportedData();
                };

                messagingTemplate.convertAndSend("/memory", objectMapper.writeValueAsString(memoryMap));

            } catch (Exception e) {
                System.err.println("[MemoryMonitor] 에러: " + e.getMessage());
            }
        }, 1, 1, TimeUnit.SECONDS);

        System.out.println("[MemoryMonitor] 시작 → OS: " + osType);
    }

    private Map<String, Object> readLinuxMemory() throws Exception {
        Map<String, Long> raw = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":\\s+");
                if (parts.length == 2) {
                    long kb = Long.parseLong(parts[1].replace(" kB", "").trim());
                    raw.put(parts[0], kb / 1024);
                }
            }
        }

        long total     = raw.getOrDefault("MemTotal",     0L);
        long free      = raw.getOrDefault("MemFree",      0L);
        long available = raw.getOrDefault("MemAvailable", 0L);
        long cached    = raw.getOrDefault("Cached",       0L);
        long used      = total - free - cached;
        double usedPercent = total > 0 ? Math.round(used * 1000.0 / total) / 10.0 : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalMb",     total);
        result.put("usedMb",      used);
        result.put("freeMb",      free);
        result.put("availableMb", available);
        result.put("cachedMb",    cached);
        result.put("usedPercent", usedPercent);
        return result;
    }

    private Map<String, Object> readWindowsMemory() throws Exception {
        com.sun.management.OperatingSystemMXBean os =
            (com.sun.management.OperatingSystemMXBean)
                java.lang.management.ManagementFactory.getOperatingSystemMXBean();

        long total = os.getTotalMemorySize() / 1024 / 1024;
        long free  = os.getFreeMemorySize()  / 1024 / 1024;
        long used  = total - free;
        double usedPercent = total > 0 ? Math.round(used * 1000.0 / total) / 10.0 : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalMb",     total);
        result.put("usedMb",      used);
        result.put("freeMb",      free);
        result.put("availableMb", free);
        result.put("cachedMb",    0L);
        result.put("usedPercent", usedPercent);
        return result;
    }

    // ──────────────────────────────────────────────
    // Storage
    // ──────────────────────────────────────────────

    private ScheduledExecutorService storageScheduler;

    public synchronized void startStorageMonitoring() {
        if (storageScheduler != null && !storageScheduler.isShutdown()) return;

        storageScheduler = Executors.newSingleThreadScheduledExecutor();
        storageScheduler.scheduleAtFixedRate(() -> {
            try {
                Map<String, Object> storageMap = switch (osType) {
                    case LINUX       -> readLinuxStorage();
                    case WINDOWS     -> readWindowsStorage();
                    case UNSUPPORTED -> unsupportedData();
                };

                messagingTemplate.convertAndSend("/storage", objectMapper.writeValueAsString(storageMap));

            } catch (Exception e) {
                System.err.println("[StorageMonitor] 에러: " + e.getMessage());
            }
        }, 1, 5, TimeUnit.SECONDS); // 스토리지는 5초 주기로 충분

        System.out.println("[StorageMonitor] 시작 → OS: " + osType);
    }

    private Map<String, Object> readLinuxStorage() {
        // 리눅스: 루트("/") 기준 단일 파티션 대표값
        return buildStorageResult(List.of(new File("/")));
    }

    private Map<String, Object> readWindowsStorage() {
        // 윈도우: 마운트된 드라이브 전체(C:\, D:\, ...)
        List<File> roots = new ArrayList<>();
        for (File root : File.listRoots()) {
            if (root.getTotalSpace() > 0) roots.add(root);
        }
        return buildStorageResult(roots);
    }

    /**
     * File 클래스로 OS 무관하게 디스크 정보 계산 (공통 로직)
     * 여러 파티션/드라이브가 있을 경우 합산해서 반환
     */
    private Map<String, Object> buildStorageResult(List<File> roots) {
        long totalGb = 0;
        long freeGb  = 0;
        long usedGb  = 0;

        List<Map<String, Object>> partitions = new ArrayList<>();

        for (File root : roots) {
            long t = root.getTotalSpace() / 1024 / 1024 / 1024;
            long f = root.getFreeSpace()  / 1024 / 1024 / 1024;
            long u = t - f;
            double pct = t > 0 ? Math.round(u * 1000.0 / t) / 10.0 : 0;

            totalGb += t;
            freeGb  += f;
            usedGb  += u;

            Map<String, Object> p = new LinkedHashMap<>();
            p.put("path",        root.getAbsolutePath());
            p.put("totalGb",     t);
            p.put("usedGb",      u);
            p.put("freeGb",      f);
            p.put("usedPercent", pct);
            partitions.add(p);
        }

        double totalPct = totalGb > 0 ? Math.round(usedGb * 1000.0 / totalGb) / 10.0 : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalGb",     totalGb);
        result.put("usedGb",      usedGb);
        result.put("freeGb",      freeGb);
        result.put("usedPercent", totalPct);
        result.put("partitions",  partitions); // 드라이브별 상세
        return result;
    }

    // ──────────────────────────────────────────────
    // 공통
    // ──────────────────────────────────────────────

    private Map<String, Object> unsupportedData() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unsupported", true);
        return result;
    }

    @PreDestroy
    public void stopMonitoring() {
        shutdownScheduler(scheduler,        "MemoryMonitor");
        shutdownScheduler(storageScheduler, "StorageMonitor");
    }

    private void shutdownScheduler(ScheduledExecutorService s, String name) {
        if (s != null) {
            s.shutdown();
            try {
                if (!s.awaitTermination(3, TimeUnit.SECONDS)) s.shutdownNow();
            } catch (InterruptedException e) {
                s.shutdownNow();
            }
            System.out.println("[" + name + "] 종료");
        }
    }
}
