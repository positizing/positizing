package injuction.detector;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * InjunctionDetector:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 02/09/2024 @ 4:03 a.m.
 */
public class InjunctionDetector {

    // list of PartsOfSpeech types here: https://surdeanu.cs.arizona.edu//mihai/teaching/ista555-fall13/readings/PennTreebankConstituents.html
    private static final String TYPE_ADVERB = "RB";
    private static final String TYPE_MODAL = "MD";
    private static final String TYPE_CONJUNCTION = "CC";

    private static final Set<String> ADVERB_MATCH_LIST = new HashSet<>();
    private static final Set<String> MODAL_MATCH_LIST = new HashSet<>();
    private static final Set<String> CONJUNCTION_MATCH_LIST = new HashSet<>();
    private static final Set<String> PROFANITY_MATCH_LIST = new HashSet<>();

    static {
        ADVERB_MATCH_LIST.add("not");
        ADVERB_MATCH_LIST.add("never");
        ADVERB_MATCH_LIST.add("n't");
        ADVERB_MATCH_LIST.add("n’t");
        MODAL_MATCH_LIST.add("should");

        // Initialize conjunction match list
        CONJUNCTION_MATCH_LIST.add("but");

        // Load profane words from 'profane_words.txt'
        try (InputStream inputStream = InjunctionDetector.class.getClassLoader().getResourceAsStream("profanity/profane_words.txt")) {
            if (inputStream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        PROFANITY_MATCH_LIST.add(line.trim().toLowerCase(Locale.ROOT));
                    }
                }
            } else {
                System.err.println("profane_words.txt not found in resources.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final StanfordCoreNLP pipeline;

    public InjunctionDetector() {
        Properties props = new Properties();
        props.put("annotators", "tokenize, pos, lemma");
        pipeline = new StanfordCoreNLP(props);
    }

    public static void main(String... args) {
        InjunctionDetector detector = new InjunctionDetector();

        List<String> testMessages = Arrays.asList(
                "I will not finish in time.",
                "I wanted to go, but it was too late.",
                "This is a clean sentence.",
                "I can't do this.",
                "She tried her best, but failed.",
                "Nothing can stop us now."
        );

        for (String message : testMessages) {
            boolean hasInjunction = detector.isInjuction(message);
            boolean hasProfanity = detector.containsProfanity(message);

            System.out.println("Message: " + message);
            System.out.println("Contains Injunction: " + hasInjunction);
            System.out.println("Contains Profanity: " + hasProfanity);
            System.out.println("-----------------------------------");
        }
    }

    public boolean isInjuction(final String message) {
        Annotation document = new Annotation(message);
        pipeline.annotate(document);
        List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);

        for (CoreLabel token : tokens) {
            String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
            String word = token.originalText().toLowerCase(Locale.ROOT);

            switch (pos) {
                case TYPE_ADVERB:
                    if (ADVERB_MATCH_LIST.contains(word)) {
                        return true;
                    }
                    break;
                case TYPE_MODAL:
                    if (MODAL_MATCH_LIST.contains(word)) {
                        return true;
                    }
                    break;
                case TYPE_CONJUNCTION:
                    if (CONJUNCTION_MATCH_LIST.contains(word)) {
                        return true;
                    }
                    break;
            }
        }
        return false;
    }

    public boolean containsProfanity(final String message) {
        Annotation document = new Annotation(message);
        pipeline.annotate(document);
        List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);

        for (CoreLabel token : tokens) {
            String lemma = token.get(CoreAnnotations.LemmaAnnotation.class).toLowerCase(Locale.ROOT);
            if (PROFANITY_MATCH_LIST.contains(lemma)) {
                return true;
            }
        }
        return false;
    }
}
