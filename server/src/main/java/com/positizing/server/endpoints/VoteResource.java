package com.positizing.server.endpoints;

import com.positizing.server.data.VoteEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;

/// VoteResource: handles feedback (votes) from users on suggestions.
///
/// Provides:
/// - POST /cache/vote to record a thumbs-up or thumbs-down
///
/// Created by James X. Nelson (James@WeTheInter.net) on 17/05/2025 @ 21:46
@ApplicationScoped
@Path("/cache")
public class VoteResource {
    private static final Logger log = Logger.getLogger(VoteResource.class);

    public static class VoteRequest {
        public String prompt;
        public String suggestion;
        public int vote;   // +1, -1, or 0
        public String token;
        public String ua;
    }

    /**
     /// POST /cache/vote
     ///
     /// If vote==0: delete any existing VoteEntry for this user/prompt/suggestion.
     /// Else: persist a new VoteEntry.
     ///
     /// Returns 204 No Content.
     */
    @POST
    @Path("/vote")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public RestResponse<Void> vote(VoteRequest req) {
        log.infof("Vote request: prompt=%s suggestion=%s vote=%d token=%s",
                req.prompt, req.suggestion, req.vote, req.token);

        if (req.vote == 0) {
            // delete existing
            long deleted = VoteEntry.delete(
                    "prompt = ?1 and suggestion = ?2 and token = ?3",
                    req.prompt, req.suggestion, req.token
            );
            log.debugf("Deleted %d vote entries", deleted);
        } else {
            // persist new
            VoteEntry entry = new VoteEntry(
                    req.prompt,
                    req.suggestion,
                    req.vote,
                    req.token,
                    req.ua
            );
            entry.persist();
            log.debugf("Persisted VoteEntry id=%d", entry.id);
        }

        return RestResponse.noContent();
    }
}