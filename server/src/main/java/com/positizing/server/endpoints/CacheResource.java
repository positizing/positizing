package com.positizing.server.endpoints;

import com.positizing.server.OpenAPIService;
import com.positizing.server.data.CacheEntry;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/// CacheResource:
///
/// Responsibilities:
/// - Expose in‑memory cache entries
/// - Expose persistent DB cache entries
/// - Return JSON payloads for UI or debugging
///
/// Created by James X. Nelson (James@WeTheInter.net) on 17/05/2025 @ 20:13
@Path("/cache")
@ApplicationScoped
public class CacheResource {

    private static final Logger log = Logger.getLogger(CacheResource.class);

    @Inject
    OpenAPIService openAPIService;

    /**
     * GET /cache/memory
     *
     * @return a JSON object whose keys are prompts and values are the cached responses
     */
    @GET
    @Path("/memory")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> getInMemoryCache() {
        // return an unmodifiable view so caller can't mutate your service cache
        return Collections.unmodifiableMap(openAPIService.getInMemoryCache());
    }

    /**
     * GET /cache/db
     *
     * @return a JSON array of { prompt, response } objects from the persistent store
     */
    @GET
    @Path("/db")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonArray getDbCache() {
        // PanacheEntity.listAll() is blocking, so be mindful of your thread model in production.
        List<CacheEntry> entries = CacheEntry.listAll();

        JsonArray arr = new JsonArray();
        for (CacheEntry e : entries) {
            JsonObject obj = new JsonObject()
                    .put("prompt", e.prompt)
                    .put("response", e.response);
            arr.add(obj);
        }
        return arr;
    }
}
