package com.mcp_gateway_k8s_store.backend.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Per-process abuse guard for the public invitation acceptance endpoint. */
@Component
public class InvitationAcceptanceRateLimiter {
    private record Window(long startedAtEpochSecond, AtomicInteger requests) { }
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean allow(String clientAddress) {
        long now = Instant.now().getEpochSecond();
        Window window = windows.compute(clientAddress, (address, current) ->
                current == null || now - current.startedAtEpochSecond() >= 60
                        ? new Window(now, new AtomicInteger()) : current);
        if (windows.size() > 10_000) windows.entrySet().removeIf(entry -> now - entry.getValue().startedAtEpochSecond() >= 60);
        return window.requests().incrementAndGet() <= 10;
    }
}
