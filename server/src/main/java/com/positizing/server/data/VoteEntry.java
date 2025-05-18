package com.positizing.server.data;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/// VoteEntry: stores user feedback on rephrase suggestions.
///
/// Fields:
/// - prompt: the original user prompt
/// - suggestion: the specific suggestion that was voted on
/// - vote: +1 for thumbs-up, -1 for thumbs-down
/// - token: anonymous session token to track unique users
/// - ua: user-agent string for logging/debugging
/// - timestamp: Unix epoch millis when the vote was recorded
///
/// Created by James X. Nelson (James@WeTheInter.net) on 17/05/2025 @ 21:44
@Entity
public class VoteEntry extends PanacheEntity {

    /// The original text the user submitted for rephrasing
    @Column(length = 1024)
    public String prompt;

    /// The specific suggestion string that was upvoted or downvoted
    @Column(length = 4096)
    public String suggestion;

    /// +1 for thumbs-up, -1 for thumbs-down
    public int vote;

    /// Anonymous session token to prevent multiple votes by the same user
    @Column(length = 128)
    public String token;

    /// The user-agent string of the client that cast the vote
    @Column(length = 512)
    public String ua;

    /// Timestamp in milliseconds since epoch when this vote was recorded
    public long timestamp;

    /// No-arg constructor required by JPA
    public VoteEntry() {}

    /// Construct a vote entry with all required fields
    public VoteEntry(String prompt,
                     String suggestion,
                     int vote,
                     String token,
                     String ua) {
        this.prompt     = prompt;
        this.suggestion = suggestion;
        this.vote       = vote;
        this.token      = token;
        this.ua         = ua;
        this.timestamp  = System.currentTimeMillis();
    }
}

