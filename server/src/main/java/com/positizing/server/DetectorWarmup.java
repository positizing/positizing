package com.positizing.server;

import injunction.detector.NegativeSpeechDetector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.quarkus.runtime.StartupEvent;

/// DetectorWarmup:
///
/// Fires one dummy detection off‑thread when Quarkus starts
/// so the CoreNLP models are cached before the first real request.
///
/// Created by James X. Nelson (James@WeTheInter.net) on 09/05/2025 @ 00:48
@ApplicationScoped
public class DetectorWarmup {

    @Inject
    NegativeSpeechDetector detector;

    void init(@Observes StartupEvent ev) {
        ExecutorService pool = Executors.newSingleThreadExecutor(r ->
                new Thread(r, "detector-warmup"));
        pool.submit(() -> {
            long t0 = System.currentTimeMillis();
            detector.isInjunction("Warm‑up.");
            System.out.printf("[DetectorWarmup] pipeline ready in %d ms%n",
                    System.currentTimeMillis() - t0);
        });
        pool.shutdown();
    }
}