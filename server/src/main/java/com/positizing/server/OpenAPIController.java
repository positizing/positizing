package com.positizing.server;

import com.positizing.server.data.VoteEntry;
import injunction.detector.NegativeSpeechDetector;
import injunction.detector.SentenceExtractionResult;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;

import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// OpenAPIController: handles /api/rephrase, now augmented with past user votes
///
/// Created by James X. Nelson (James@WeTheInter.net) on 12/11/2024 @ 8:49 p.m.
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OpenAPIController {

    private static final Logger log = Logger.getLogger(OpenAPIController.class);

    @Inject
    OpenAPIService openAIService;

    @Inject
    NegativeSpeechDetector detector;

    /**
     * Client payload for rephrase requests.
     */
    public static class InputRequest {
        public String prompt;
        public String token;
        public String ua;
        public boolean force;
    }


    /**
     * Server response including suggestions and per‑suggestion votes.
     */
    public static class RephraseResponse {
        public String errorMessage;
        public List<String> suggestions;
        public Map<String, Integer> votes;
    }

    /**
     * POST /api/rephrase
     *  • Builds enriched prompt (async)
     *  • Calls OpenAIService
     *  • Parses JSON array of suggestions
     *  • Fetches latest vote for each suggestion for this user token
     *  • Returns JSON { suggestions: [...], votes: { "...": 1, "...": -1, ... } }
     */
    @POST
    @Path("/rephrase")
    public Uni<RestResponse<RephraseResponse>> rephrase(InputRequest req) {
        log.infof("Entered /api/rephrase: prompt='%s', token=%s, ua=%s, force=%b",
                req.prompt, req.token, req.ua, req.force);

        // ---- 1) Synchronous negative‐speech detection ----
        String text = req.prompt == null ? "" : req.prompt.trim();
        log.debugf("Trimmed prompt to '%s'", text);

        if (!text.isEmpty() && Character.isLetterOrDigit(text.charAt(text.length() - 1))) {
            text = text + ".";
            log.debugf("Appended period to text, now '%s'", text);
        }

        SentenceExtractionResult extraction = detector.extractCompleteSentences(text);
        List<String> sentences = extraction.getCompleteSentences();
        log.infof("extractCompleteSentences returned %d sentences", sentences.size());

        boolean hasNegative = sentences.stream().peek(sent -> {
            boolean inj = detector.isInjunction(sent);
            boolean conj = detector.hasConjunction(sent);
            log.debugf("Sentence '%s' -> isInjunction=%b, hasConjunction=%b", sent, inj, conj);
        }).anyMatch(sent -> detector.isInjunction(sent) || detector.hasConjunction(sent));

        log.infof("Negative speech detected: %b", hasNegative);
        if (!req.force && !hasNegative) {
            log.info("No negative speech and force==false -> returning empty suggestions");
            RephraseResponse empty = new RephraseResponse();
            empty.suggestions = Collections.emptyList();
            empty.votes       = Collections.emptyMap();
            return Uni.createFrom().item(RestResponse.ok(empty));
        }

        // ---- 2) Async: build prompt, call OpenAI, and lookup votes ----
        log.debug("Offloading prompt build and OpenAI call to worker thread");
        return Uni.createFrom().item(req)
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .map(r -> {
                    log.debug("Building enriched prompt");
                    return buildEnrichedPrompt(r.prompt);
                })
                .flatMap(enriched -> {
                    log.info("Calling OpenAIService.getResponse");
                    log.debugf("Enriched prompt: %s", enriched);
                    return openAIService.getResponse(enriched);
                })
                .flatMap(rawJson -> Uni.createFrom().item(() -> {
                            log.debugf("Received raw JSON from OpenAI: %s", rawJson);
                            final String raw;
                            int start = rawJson.indexOf('[');
                            int end = rawJson.lastIndexOf(']');
                            RephraseResponse resp = new RephraseResponse();
                            if (start >= 0 && end >= 0 && end > start) {
                                raw = rawJson.substring(start, end + 1);
                            } else {
                                log.warnf("No valid JSON array found in response: %s", rawJson);
                                resp.votes = Collections.emptyMap();
                                resp.suggestions = Collections.emptyList();
                                resp.errorMessage = "Received invalid response from server. Please try a different suggestion!";
                                // TODO: consider purging cache for this input, to help it recover.
                                return RestResponse.status(RestResponse.Status.INTERNAL_SERVER_ERROR, resp);
                            }

                            List<String> suggestions = parseJsonArray(raw);
                            log.infof("Parsed %d suggestions", suggestions.size());

                            Map<String,Integer> votesMap = suggestions.stream()
                                    .collect(Collectors.toMap(
                                            s -> s,
                                            s -> {
                                                VoteEntry ve = VoteEntry.<VoteEntry>find(
                                                        "prompt = ?1 and suggestion = ?2 and token = ?3 order by timestamp desc",
                                                        req.prompt, s, req.token
                                                ).firstResult();
                                                int v = ve != null ? ve.vote : 0;
                                                log.debugf("Lookup vote for '%s' -> %d", s, v);
                                                return v;
                                            }
                                    ));
                            log.infof("Compiled votes map: %s", votesMap);
                            resp.suggestions = suggestions;
                            resp.votes       = votesMap;
                            log.info("Returning populated RephraseResponse");
                            return RestResponse.ok(resp);
                        })
                        .runSubscriptionOn(Infrastructure.getDefaultExecutor()))
                .onFailure().recoverWithItem(e -> {
                    log.error("Error in /api/rephrase", e);
                    return RestResponse.<RephraseResponse>status(
                            RestResponse.Status.INTERNAL_SERVER_ERROR, null
                    );
                });
    }


    /**
     * Helper: parse a JSON array-of-strings into a List<String>.
     */
    private List<String> parseJsonArray(String jsonArrayStr) {
        try (JsonReader jr = Json.createReader(new StringReader(jsonArrayStr))) {
            JsonArray arr = jr.readArray();
            return arr.getValuesAs(JsonString.class)
                    .stream()
                    .map(JsonString::getString)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.errorf("Failed to parse OpenAI JSON response: %s", jsonArrayStr);
            throw e;
        }
    }

    /**
     * buildEnrichedPrompt: consults VoteEntry for this prompt, collects upvoted
     * and downvoted samples, then applies to the base instruction prompt.
     * This method runs on a worker thread to avoid blocking the IO event loop.
     */
    private String buildEnrichedPrompt(String userInput) {
        // Fetch all votes for this prompt (blocking DB call in worker context)
        List<VoteEntry> votes = VoteEntry.list("prompt", userInput);

        // Aggregate vote scores by suggestion
        Map<String, Long> scores = votes.stream()
                .collect(Collectors.groupingBy(v -> v.suggestion,
                        Collectors.summingLong(v -> v.vote)));

        // Top 7 upvoted suggestions (highest score first)
        List<String> upvoted = scores.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(7)
                .collect(Collectors.toList());

        // Top 7 downvoted suggestions (lowest score first)
        List<String> downvoted = scores.entrySet().stream()
                .filter(e -> e.getValue() < 0)
                .sorted(Map.Entry.comparingByValue())  // natural order (lowest to highest)
                .map(Map.Entry::getKey)
                .limit(7)
                .collect(Collectors.toList());

        // Base prompt with guidelines (including misspelling correction)
        String base = String.format(
                "You are an expert in transforming negative speech into positive, empowering language based on Eric Berne's Transactional Analysis and Dr. Hawkins' Scale of Consciousness.\n\n" +
                        "Identify any negative speech patterns in the following sentence and provide positive, rephrased versions according to these guidelines:\n" +
                        "- Replace words like \"don't\", \"isn't\", \"can't\", \"not\", \"try\", and \"should.\"\n" +
                        "- Encourage ownership of feelings by avoiding phrases like \"You make me feel...\" and instead using \"I feel [emotion word]...\"\n" +
                        "- Replace \"I feel that...\" with \"I feel [emotion word]...\"\n" +
                        "- If the original statement must deal with negative concepts, it should maintain the original intent, but use more positive language constructs\n" +
                        "Also, correct common misspellings such as \"wont\" for \"won't\" or \"dn't\" for \"don't\".\n" +
                        "Make sure responses avoid the use of injunctions or other negative speech patterns as much as possible.\n\n" +
                        "Sentence: \"%s\"\n\n" +
                        "Provide the response as a JSON array of suggestions without any additional text or formatting.",
                userInput
        );

        StringBuilder promptBuilder = new StringBuilder(base);
        if (!upvoted.isEmpty()) {
            promptBuilder.append("\n\nSample of previously upvoted suggestions:\n");
            upvoted.forEach(s -> promptBuilder.append("- ").append(s).append("\n"));
        }
        if (!downvoted.isEmpty()) {
            promptBuilder.append("\nSample of previously downvoted suggestions:\n");
            downvoted.forEach(s -> promptBuilder.append("- ").append(s).append("\n"));
        }

        return promptBuilder.toString();
    }

    /**
     * Simple Pair helper (since Java lacks a built‑in tuple).
     */
    private static class Pair<A,B> {
        final A first;
        final B second;
        Pair(A a, B b) { this.first = a; this.second = b; }
    }
}