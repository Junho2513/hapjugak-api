package com.example.hapjugakapi.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 데이터가 바뀌면 접속 중인 브라우저에 알린다.
 * 내용은 보내지 않고 "바뀌었다"는 신호만 보낸다.
 * 무엇이 어떻게 바뀌었는지는 브라우저가 다시 조회해서 알아낸다.
 */
@Service
public class StreamService {

    /** 30분 뒤 끊고, 브라우저가 알아서 다시 붙는다. */
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        emitters.add(emitter);
        send(emitter, "connected", "ok");
        return emitter;
    }

    public void publishChanged() {
        for (SseEmitter emitter : emitters) {
            send(emitter, "changed", String.valueOf(System.currentTimeMillis()));
        }
    }

    /**
     * 20초마다 주석 한 줄을 흘려보낸다.
     * 중간의 프록시와 브라우저는 오래 조용한 연결을 끊어버리기 때문에,
     * 살아 있다는 표시를 주기적으로 보내야 한다.
     */
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        for (SseEmitter emitter : emitters) {
            send(emitter, "ping", "");
        }
    }

    private void send(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException e) {
            /* 이미 끊긴 연결. 목록에서 빼고 조용히 넘어간다. */
            emitters.remove(emitter);
        }
    }

    public int connectionCount() {
        return emitters.size();
    }
}