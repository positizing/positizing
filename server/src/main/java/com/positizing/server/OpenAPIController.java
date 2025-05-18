package com.positizing.server;

import com.positizing.server.data.VoteEntry;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

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

    @Inject
    OpenAPIService openAIService;

    /// DTO matching client-side JSON payload
    public static class InputRequest {
        public String prompt;
        public String token;
        public String ua;
    }

    /// POST /api/rephrase
    /// - Offloads vote lookup and prompt construction to a worker thread
    /// - Calls OpenAIService with the enriched prompt
    @POST
    @Path("/rephrase")
    public Uni<RestResponse<String>> rephrase(InputRequest request) {
        // Offload buildEnrichedPrompt (which performs DB access) to worker
        return Uni.createFrom().item(request.prompt)
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .map(this::buildEnrichedPrompt)
                .flatMap(enrichedPrompt -> openAIService.getResponse(enrichedPrompt))
                .onItem().transform(response -> RestResponse.ok(response))
                .onFailure().recoverWithItem(e -> RestResponse.status(
                        RestResponse.Status.INTERNAL_SERVER_ERROR,
                        e.getMessage()
                ));
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

        // Separate into upvoted and downvoted lists
        List<String> upvoted = scores.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<String> downvoted = scores.entrySet().stream()
                .filter(e -> e.getValue() < 0)
                .map(Map.Entry::getKey)
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
}

