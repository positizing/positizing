package com.positizing.server.endpoints;

import com.positizing.server.data.VoteEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

/// VoteResource: handles feedback (votes) from users on suggestions.
///
/// Provides:
/// - POST /cache/vote to record a thumbs-up or thumbs-down
///
/// Created by James X. Nelson (James@WeTheInter.net) on 17/05/2025 @ 21:46
@Path("/cache")
@ApplicationScoped
public class VoteResource {

    private static final Logger log = Logger.getLogger(VoteResource.class);

    /// Data class representing the incoming JSON payload for a vote
    public static class VoteRequest {
        /// The original text prompt
        public String prompt;
        /// The suggestion that was voted on
        public String suggestion;
        /// +1 for thumbs-up, -1 for thumbs-down
        public int vote;
        /// Anonymous session token
        public String token;
        /// User-agent string
        public String ua;
    }

    /// Record a user vote for a suggestion
    ///
    /// @param req VoteRequest JSON payload
    /// @return HTTP 204 No Content on success
    @POST
    @Path("/vote")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response vote(VoteRequest req) {
        // Log the incoming vote details
        log.infof("Received vote: prompt='%s', suggestion='%s', vote=%d, token='%s', ua='%s'",
                req.prompt, req.suggestion, req.vote, req.token, req.ua);

        // Persist the vote entry
        VoteEntry entry = new VoteEntry(
                req.prompt,
                req.suggestion,
                req.vote,
                req.token,
                req.ua
        );
        entry.persist();

        log.debugf("Vote persisted with id %d", entry.id);
        return Response.noContent().build();
    }
}

