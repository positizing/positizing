package com.positizing.server;

import injunction.detector.NegativeSpeechDetector;
import injunction.detector.SentenceExtractionResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;
import io.smallrye.mutiny.Uni;

import java.util.ArrayList;
import java.util.List;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
/// DetectorController:
///
///
/// Created by James X. Nelson (James@WeTheInter.net) on 08/05/2025 @ 22:30
public class DetectorController {

    @Inject
    NegativeSpeechDetector detector;

    // ---------- DTOs ----------
    public static final class DetectRequest {
        public String text;
    }
    public static final class Span {
        public int start;
        public int end;
        public String text;
        public Span(int s, int e, String t) { start = s; end = e; text = t; }
    }
    public static final class DetectResponse {
        public boolean needsReplacement;
        public List<Span> negativeSpans = new ArrayList<>();
    }

    // ---------- Endpoint ----------
    @POST
    @Path("/detect")
    public Uni<RestResponse<DetectResponse>> detect(DetectRequest req) {
        return Uni.createFrom().item(() -> {
            DetectResponse out = new DetectResponse();

            String text = req.text.trim();
            if (!text.isEmpty() && Character.isLetterOrDigit(text.charAt(text.length() - 1))) {
                text = text + ".";
            }
            SentenceExtractionResult extraction =
                    detector.extractCompleteSentences(text);

            for (String sent : extraction.getCompleteSentences()) {
                boolean negative =
                        detector.isInjunction(sent) || detector.hasConjunction(sent);

                if (negative) {
                    int idx = text.indexOf(sent);
                    if (idx >= 0) {
                        out.negativeSpans.add(new Span(idx, idx + sent.length(), sent));
                    }
                }
            }
            out.needsReplacement = !out.negativeSpans.isEmpty();
            return RestResponse.ok(out);
        });
    }
}