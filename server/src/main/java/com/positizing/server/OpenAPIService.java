package com.positizing.server;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.vertx.UniHelper;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenAPIService:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 12/11/2024 @ 8:49 p.m.
 */

@ApplicationScoped
public class OpenAPIService {

    @ConfigProperty(name = "openai.api.key")
    String openaiApiKey;

    @Inject
    WebClient webClient;

    // In-memory cache
    private ConcurrentHashMap<String, String> inMemoryCache = new ConcurrentHashMap<>();

    public Uni<String> getResponse(String prompt) {
        // First, check in-memory cache
        String cachedResponse = inMemoryCache.get(prompt);
        if (cachedResponse != null) {
            System.out.println("Returning cached response: " + cachedResponse);
            return Uni.createFrom().item(cachedResponse);
        }

        // Offload blocking database operation to a worker thread
        return Uni.createFrom().<CacheEntry>item(() -> {
                    // Blocking call to fetch from database
                    return CacheEntry.find("prompt", prompt).firstResult();
                }).runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .onItem().transformToUni((CacheEntry cachedEntry) -> {
                    if (cachedEntry != null) {
                        System.out.println("Cached entrry: " + cachedEntry);
                        // Store in in-memory cache for faster access next time
                        inMemoryCache.put(prompt, cachedEntry.response);
                        return Uni.createFrom().item(cachedEntry.response);
                    } else {
                        System.out.println("Not cached, calling openAPI: : " + prompt);
                        // If not cached, call OpenAI API
                        return callOpenAI(prompt).onItem().invoke(response -> {
                            System.out.println("Got final response: " + response);
                            // Store response in both caches
                            inMemoryCache.put(prompt, response);
                            System.out.println("Got here too: " + response);
                            // Save to database asynchronously
                            offloadSaveToDatabase(prompt, response);
                            System.out.println("Fini: " + response);
                        });
                    }
                });
    }
    // Method to offload the save operation to a worker thread
    private void offloadSaveToDatabase(String prompt, String response) {
        Uni.createFrom().item(() -> {
                    saveToDatabase(prompt, response);
                    return null;
                }).runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .subscribe().with(
                        unused -> {}, // Handle success
                        Throwable::printStackTrace // Handle failure
                );
    }

    @Transactional
    public void saveToDatabase(String prompt, String response) {
        CacheEntry entry = new CacheEntry(prompt, response);
        entry.persist();
    }

    private Uni<String> callOpenAI(String prompt) {
        JsonObject requestBody = new JsonObject()
                .put("model", "gpt-4o-mini") // Use "gpt-4" if you have access
//                .put("model", "gpt-3.5-turbo") // Use "gpt-4" if you have access
                .put("messages", new JsonArray()
                        .add(new JsonObject().put("role", "user").put("content", prompt)))
                .put("max_tokens", 150)
                .put("temperature", 0.7);

        final Future<String> future = webClient.postAbs("https://api.openai.com/v1/chat/completions")
                .putHeader("Content-Type", "application/json")
                .putHeader("Authorization", "Bearer " + openaiApiKey)
                .sendJsonObject(requestBody)
                .transform(response -> {
                    System.out.println("Got response: " + response + " : " + response.succeeded());
                    if (response.succeeded()) {
                        JsonObject jsonResponse = response.result().bodyAsJsonObject();
                        System.out.println("Got json: " + jsonResponse);
                        String result = jsonResponse
                                .getJsonArray("choices")
                                .getJsonObject(0)
                                .getJsonObject("message")
                                .getString("content")
                                .trim();
                        System.out.println("Got openAPI result: " + result);
                        return Future.succeededFuture(result);
                    } else {
                        throw new RuntimeException("OpenAI API error: ", response.cause());
                    }
                });
        return Uni.createFrom().emitter(emitter -> {
            future.onSuccess(emitter::complete)
                    .onFailure(emitter::fail);
        });
    }
}
