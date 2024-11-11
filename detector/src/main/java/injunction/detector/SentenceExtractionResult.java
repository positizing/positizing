package injunction.detector;

import java.util.List;

/**
 * SentenceExtractionResult:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 11/11/2024 @ 12:07 a.m.
 */ // New class for sentence extraction result
public class SentenceExtractionResult {
    private final List<String> completeSentences;
    private final String remainingText;

    public SentenceExtractionResult(List<String> completeSentences, String remainingText) {
        this.completeSentences = completeSentences;
        this.remainingText = remainingText;
    }

    public List<String> getCompleteSentences() {
        return completeSentences;
    }

    public String getRemainingText() {
        return remainingText;
    }
}
