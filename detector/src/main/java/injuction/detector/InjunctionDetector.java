package injuction.detector;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.CoreMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * InjunctionDetector:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 02/09/2024 @ 4:03 a.m.
 */
public class InjunctionDetector {

    private static final String TYPE_ADVERB = "RB";
    private static final String TYPE_MODAL = "MD";
    private static final String TYPE_CONJUNCTION = "CC";

    private static final Set<String> ADVERB_MATCH_LIST = new HashSet<>();
    private static final Set<String> MODAL_MATCH_LIST = new HashSet<>();
    private static final Set<String> CONJUNCTION_MATCH_LIST = new HashSet<>();
    private static final Set<String> PROFANITY_MATCH_LIST = new HashSet<>();

    private static final Map<String, String> SUBJECT_TO_OBJECT_PRONOUN = new HashMap<>();
    private static final Map<String, String> OBJECT_TO_SUBJECT_PRONOUN = new HashMap<>();

    static {
        SUBJECT_TO_OBJECT_PRONOUN.put("i", "me");
        SUBJECT_TO_OBJECT_PRONOUN.put("you", "you");
        SUBJECT_TO_OBJECT_PRONOUN.put("he", "him");
        SUBJECT_TO_OBJECT_PRONOUN.put("she", "her");
        SUBJECT_TO_OBJECT_PRONOUN.put("we", "us");
        SUBJECT_TO_OBJECT_PRONOUN.put("they", "them");

        OBJECT_TO_SUBJECT_PRONOUN.put("me", "I");
        OBJECT_TO_SUBJECT_PRONOUN.put("you", "you");
        OBJECT_TO_SUBJECT_PRONOUN.put("him", "he");
        OBJECT_TO_SUBJECT_PRONOUN.put("her", "she");
        OBJECT_TO_SUBJECT_PRONOUN.put("us", "we");
        OBJECT_TO_SUBJECT_PRONOUN.put("them", "they");

        ADVERB_MATCH_LIST.add("not");
        ADVERB_MATCH_LIST.add("never");
        ADVERB_MATCH_LIST.add("n't");
        ADVERB_MATCH_LIST.add("n’t");
        MODAL_MATCH_LIST.add("should");

        // Initialize conjunction match list
        CONJUNCTION_MATCH_LIST.add("but");

        // Load profane words from 'profane_words.txt' if needed

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
    private final TaskExecutor taskExecutor;

    public InjunctionDetector(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
        Properties props = new Properties();
        props.put("annotators", "tokenize, ssplit, pos, lemma, parse, depparse");
        pipeline = new StanfordCoreNLP(props);
    }

    public static void main(String... args) {
        InjunctionDetector detector = new InjunctionDetector(new DesktopTaskExecutor());

        List<String> testSentences = Arrays.asList(
                "She is not only talented but also hardworking.",
                "He is not only smart but also kind.",
                "They were not only exhausted but also hungry.",
                "The project is not only ambitious but also feasible.",
                "This is a regular sentence without the pattern."
        );

        for (String testSentence : testSentences) {
            boolean hasInjunction = detector.isInjuction(testSentence);
            String improvedSentence = detector.suggestImprovedSentence(testSentence);

            System.out.println("Original Sentence: " + testSentence);
            System.out.println("Contains Injunction: " + hasInjunction);
            System.out.println("Improved Sentence: " + improvedSentence);
            System.out.println("-----------------------------------");
        }
    }

    public boolean isInjuction(final String message) {
        // First, check for the specific pattern "not only ... but also ..."
        if (detectNotOnlyButAlsoPattern(message)) {
            return true;
        }

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

    public void transformSentence(final String sentence, Consumer<String> callback) {
        taskExecutor.execute(() -> {
            String transformed = transformSentenceInternal(sentence);
            callback.accept(transformed);
        });
    }

    /**
     * Transforms sentences of the form "Subject makes Object feel [Complement]"
     * into "NewSubject feel [Complement] with NewObject", keeping pronouns consistent.
     *
     * @param sentence The input sentence to transform.
     * @return The transformed sentence if the pattern is matched; otherwise, returns the original sentence.
     */
    private String transformSentenceInternal(final String sentence) {
        // Annotate the sentence using Stanford CoreNLP
        Annotation document = new Annotation(sentence);
        pipeline.annotate(document);

        // Get the list of sentences (assuming the input is a single sentence)
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        if (sentences.isEmpty()) {
            return sentence; // Return the original sentence if no sentences are found
        }

        CoreMap cmSentence = sentences.get(0);

        // Obtain the dependency parse of the sentence
        SemanticGraph dependencies = cmSentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);

        // Identify the main verb (root of the dependency tree)
        IndexedWord rootVerb = dependencies.getFirstRoot();
        String rootLemma = rootVerb.get(CoreAnnotations.LemmaAnnotation.class);

        // Initialize variables to hold grammatical components
        IndexedWord subject = null;         // The subject of the sentence (nsubj)
        IndexedWord object = null;          // The object of the verb (dobj)
        IndexedWord complementVerb = null;  // The complement verb (xcomp or ccomp)

        // Iterate over the dependency edges to find grammatical relations
        for (SemanticGraphEdge edge : dependencies.edgeIterable()) {
            GrammaticalRelation reln = edge.getRelation();
            IndexedWord governor = edge.getGovernor();
            IndexedWord dependent = edge.getDependent();

            // Find the nominal subject (nsubj) of the main verb
            if (reln.getShortName().equals("nsubj") && governor.equals(rootVerb)) {
                subject = dependent; // e.g., "She"
            }
            // Find the direct object (dobj) of the main verb
            else if (reln.getShortName().equals("dobj") && governor.equals(rootVerb)) {
                object = dependent; // e.g., "me"
            }
            // Find the complement verb (xcomp or ccomp) of the main verb
            else if ((reln.getShortName().equals("xcomp") || reln.getShortName().equals("ccomp")) && governor.equals(rootVerb)) {
                complementVerb = dependent; // e.g., "feel"
            }
        }

        // Check if all necessary components are found
        if (subject != null && object != null && complementVerb != null) {
            // Swap pronouns to keep consistency
            // Convert the object pronoun to the new subject pronoun
            String newSubject = OBJECT_TO_SUBJECT_PRONOUN.getOrDefault(object.originalText().toLowerCase(), object.originalText());
            // Convert the subject pronoun to the new object pronoun
            String newObject = SUBJECT_TO_OBJECT_PRONOUN.getOrDefault(subject.originalText().toLowerCase(), subject.originalText());

            // Capitalize the new subject for proper sentence formatting
            newSubject = capitalizeFirstLetter(newSubject);

            // Extract the complement phrase (e.g., "feel happy")
            List<IndexedWord> complementWords = dependencies.getSubgraphVertices(complementVerb).stream()
                    // Sort the words according to their positions in the sentence
                    .sorted(Comparator.comparingInt(IndexedWord::index)).collect(Collectors.toList());

            // Build the complement phrase by concatenating the words
            StringBuilder complementBuilder = new StringBuilder();
            for (IndexedWord word : complementWords) {
                complementBuilder.append(word.originalText());
                complementBuilder.append(word.after()); // Preserve whitespace
            }
            String complement = complementBuilder.toString().trim();

            // Reconstruct the sentence in the new format
            // "NewSubject Complement with NewObject."
            String transformedSentence = String.format("%s %s with %s.",
                    newSubject, complement, newObject);

            return transformedSentence;
        }

        // Return the original sentence if the pattern is not matched
        return sentence;
    }

    public String suggestImprovedSentence(final String message) {
        if (detectNotOnlyButAlsoPattern(message)) {
            // Use parse tree to reconstruct the sentence
            return rewriteNotOnlyButAlsoSentenceUsingParseTree(message);
        }
        // Return the original message if no improvement is needed
        return message;
    }

    private boolean hasNextWord(List<CoreLabel> tokens, int currentIndex, String expectedNextWord) {
        return currentIndex + 1 < tokens.size() &&
                tokens.get(currentIndex + 1).originalText().toLowerCase(Locale.ROOT).equals(expectedNextWord);
    }

    private boolean detectNotOnlyButAlsoPattern(String message) {
        // Annotate the message
        Annotation document = new Annotation(message);
        pipeline.annotate(document);

        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        if (sentences.isEmpty()) {
            return false;
        }

        CoreMap sentence = sentences.get(0);
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);

        boolean foundNotOnly = false;
        boolean foundButAlso = false;

        for (int i = 0; i < tokens.size(); i++) {
            String word = tokens.get(i).originalText().toLowerCase(Locale.ROOT);

            if (word.equals("not") && hasNextWord(tokens, i, "only")) {
                foundNotOnly = true;
            }
            if (word.equals("but") && hasNextWord(tokens, i, "also")) {
                foundButAlso = true;
            }
        }

        return foundNotOnly && foundButAlso;
    }

    private String rewriteNotOnlyButAlsoSentenceUsingParseTree(String message) {
        // Annotate the message
        Annotation document = new Annotation(message);
        pipeline.annotate(document);

        // Assuming the message contains only one sentence
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        if (sentences.isEmpty()) {
            return message; // Return original message if no sentences found
        }

        CoreMap sentence = sentences.get(0);

        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);

        int notOnlyIndex = -1;
        int butAlsoIndex = -1;

        for (int i = 0; i < tokens.size(); i++) {
            String word = tokens.get(i).originalText().toLowerCase(Locale.ROOT);
            if (word.equals("not") && i + 1 < tokens.size() && tokens.get(i + 1).originalText().toLowerCase(Locale.ROOT).equals("only")) {
                notOnlyIndex = i;
            }
            if (word.equals("but") && i + 1 < tokens.size() && tokens.get(i + 1).originalText().toLowerCase(Locale.ROOT).equals("also")) {
                butAlsoIndex = i;
            }
        }

        if (notOnlyIndex == -1 || butAlsoIndex == -1) {
            return message; // Return original message if pattern not found
        }

        // Extract phrases after "not only" and "but also"
        String firstPhrase = extractPhrase(tokens, notOnlyIndex + 2, butAlsoIndex);
        String secondPhrase = extractPhrase(tokens, butAlsoIndex + 2, tokens.size());

        // Reconstruct the sentence
        StringBuilder improvedSentence = new StringBuilder();
        // Include tokens before "not only"
        for (int i = 0; i < notOnlyIndex; i++) {
            improvedSentence.append(tokens.get(i).originalText());
            improvedSentence.append(tokens.get(i).after());
        }
        improvedSentence.append("both");
        improvedSentence.append(tokens.get(notOnlyIndex + 1).after()); // Whitespace after "only"
        improvedSentence.append(firstPhrase.trim());
        improvedSentence.append(tokens.get(butAlsoIndex - 1).after()); // Whitespace before "but"
        improvedSentence.append("and");
        improvedSentence.append(tokens.get(butAlsoIndex + 1).after()); // Whitespace after "also"
        improvedSentence.append(secondPhrase.trim());

        // Preserve capitalization
        if (Character.isUpperCase(message.charAt(0))) {
            improvedSentence.setCharAt(0, Character.toUpperCase(improvedSentence.charAt(0)));
        }

        return improvedSentence.toString();
    }

    private String extractPhrase(List<CoreLabel> tokens, int startIndex, int endIndex) {
        StringBuilder phrase = new StringBuilder();
        for (int i = startIndex; i < endIndex && i < tokens.size(); i++) {
            phrase.append(tokens.get(i).originalText());
            phrase.append(tokens.get(i).after()); // Preserve whitespace
        }
        return phrase.toString();
    }

    public boolean containsProfanity(final String message) {
        // Same as previous implementation
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
    private String capitalizeFirstLetter(String word) {
        if (word == null || word.isEmpty()) {
            return word;
        }
        return word.substring(0, 1).toUpperCase() + word.substring(1);
    }
}