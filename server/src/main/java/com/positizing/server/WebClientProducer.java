package com.positizing.server;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * WebClientProducer:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 12/11/2024 @ 8:52 p.m.
 */

@ApplicationScoped
public class WebClientProducer {

    @Produces
    @Singleton
    public WebClient createWebClient(Vertx vertx) {
        return WebClient.create(vertx);
    }
}