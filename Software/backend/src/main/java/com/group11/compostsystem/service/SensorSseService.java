package com.group11.compostsystem.service;

import com.group11.compostsystem.dto.SensorReadingResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SensorSseService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        final SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        this.emitters.add(emitter);

        emitter.onCompletion(() -> this.emitters.remove(emitter));
        emitter.onTimeout(() -> this.emitters.remove(emitter));
        emitter.onError((e) -> this.emitters.remove(emitter));

        return emitter;
    }

    public void publish(SensorReadingResponse reading) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("sensor-reading").data(reading));
            } catch (IOException ex) {
                emitters.remove(emitter);
            }
        }
    }

    public void publishConnectionStatus(Object status) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("sensor-connection").data(status));
            } catch (IOException ex) {
                emitters.remove(emitter);
            }
        }
    }
}
