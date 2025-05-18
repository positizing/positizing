package com.positizing.server;

import injunction.detector.DesktopTaskExecutor;
import injunction.detector.NegativeSpeechDetector;
import injunction.detector.TaskExecutor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/// DetectorProducer:
///
/// Provides singleton instances for CDI injection.
///
/// Created by James X. Nelson (James@WeTheInter.net) on 08/05/2025 @ 22:28
@ApplicationScoped
public class DetectorProducer {

    @Produces
    @Singleton
    TaskExecutor taskExecutor() {
        return new DesktopTaskExecutor();
    }

    @Produces
    @Singleton
    NegativeSpeechDetector negativeSpeechDetector(TaskExecutor exec) {
        return new NegativeSpeechDetector(exec);    // heavy ⇒ keep one per JVM
    }
}