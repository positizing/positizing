package com.positizing.server;


import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;

import io.smallrye.mutiny.Uni;
/**
 * OpenAPIController:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 12/11/2024 @ 8:49 p.m.
 */

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OpenAPIController {

    @Inject
    OpenAPIService openAIService;

    @POST
    @Path("/rephrase")
    public Uni<RestResponse<String>> rephrase(InputRequest request) {
        String prompt = generatePrompt(request.userInput);

        return openAIService.getResponse(prompt)
                .onItem().transform(response -> RestResponse.ok(response))
                .onFailure().recoverWithItem(e -> RestResponse.status(RestResponse.Status.INTERNAL_SERVER_ERROR, e.getMessage()));
    }

    private String generatePrompt(String userInput) {
        return String.format(
                "You are an expert in transforming negative speech into positive, empowering language based on Eric Berne's Transactional Analysis and Dr. Hawkins' Scale of Consciousness.\n\n" +
                        "Identify any negative speech patterns in the following sentence and provide positive, rephrased versions according to these guidelines:\n" +
                        "- Replace words like \"don't,\" \"isn't,\" \"can't,\" \"not,\" \"try,\" and \"should.\"\n" +
                        "- Encourage ownership of feelings by avoiding phrases like \"You make me feel...\" and instead using \"I feel [emotion word]...\"\n" +
                        "- Replace \"I feel that...\" with \"I feel [emotion word]...\"\n\n" +
                        "Sentence: \"%s\"\n\n" +
                        "Provide the response as a JSON array of suggestions without any additional text or formatting.", userInput);
    }

    public static class InputRequest {
        public String userInput;
    }
}