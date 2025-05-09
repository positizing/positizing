package injunction.detector;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The {@code InjunctionDetector} class provides methods to analyze and transform sentences
 * based on specific linguistic patterns using the Stanford CoreNLP library.
 *
 * <p>This class offers functionalities including:
 * <ul>
 *   <li>Detecting injunctions (negative expressions) in sentences.</li>
 *   <li>Detecting profanity in sentences.</li>
 *   <li>Transforming sentences by restructuring or rephrasing them based on certain patterns.</li>
 *   <li>Providing suggestions for improving sentences that contain specific conjunctions or patterns.</li>
 * </ul>
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Asynchronous processing using a {@link TaskExecutor} to avoid blocking the main thread.</li>
 *   <li>Integration with Stanford CoreNLP for natural language processing tasks such as tokenization, lemmatization, part-of-speech tagging, and parsing.</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * TaskExecutor executor = new TaskExecutor();
 * InjunctionDetector detector = new InjunctionDetector(executor);
 *
 * String sentence = "She doesn't make me feel sad.";
 * detector.transformSentence(sentence, transformedSentence -> {
 *     System.out.println("Transformed Sentence: " + transformedSentence);
 * });
 * }</pre>
 *
 * <p>This would output:
 * <pre>
 * Transformed Sentence: I don't feel sad with her.
 * </pre>
 *
 * <p>Note: All methods that perform analysis or transformations asynchronously accept a {@code Consumer} callback to handle the results.
 *
 * <p><b>Author:</b> James X. Nelson (James@WeTheInter.net)
 * <br><b>Created:</b> 02/09/2024 @ 4:03 a.m.
 */
public class NegativeSpeechDetector {

    // List of Parts of Speech (POS) tags can be found here:
    // https://surdeanu.cs.arizona.edu/mihai/teaching/ista555-fall13/readings/PennTreebankConstituents.html

    /** Part-of-speech tag for adverbs (e.g., "not", "never"). */
    private static final String POS_ADVERB = "RB";

    /** Part-of-speech tag for modal verbs (e.g., "should", "could"). */
    private static final String POS_MODAL = "MD";

    /** Part-of-speech tag for coordinating conjunctions (e.g., "but", "and"). */
    private static final String POS_CONJUNCTION = "CC";

    /** Set of adverbs that match our criteria, such as negative adverbs. */
    private static final Set<String> ADVERB_MATCH_SET = new HashSet<>();

    /** Set of modal verbs that match our criteria. */
    private static final Set<String> MODAL_MATCH_SET = new HashSet<>();

    /** Set of conjunctions that match our criteria. */
    private static final Set<String> CONJUNCTION_MATCH_SET = new HashSet<>();

    /** Set of profane words to detect in sentences. */
    private static final Set<String> PROFANITY_WORD_SET = new HashSet<>();

    /** Set of reflexive pronouns (e.g., "myself", "yourself"). */
    private static final Set<String> REFLEXIVE_PRONOUNS = new HashSet<>();

    /** Map from subject pronouns to object pronouns (e.g., "I" -> "me"). */
    private static final Map<String, String> SUBJECT_TO_OBJECT_PRONOUN = new HashMap<>();

    /** Map from object pronouns to subject pronouns (e.g., "me" -> "I"). */
    private static final Map<String, String> OBJECT_TO_SUBJECT_PRONOUN = new HashMap<>();

    /** Map of irregular verbs from base form to past tense. */
    private static final Map<String, String> IRREGULAR_PAST_TENSE_VERBS = new HashMap<>();

    /** Map of irregular verbs from base form to third person singular present tense. */
    private static final Map<String, String> IRREGULAR_PRESENT_TENSE_VERBS = new HashMap<>();

    /** Map of negations to their affirmative forms (e.g., "can't" -> "can"). */
    private static final Map<String, String> NEGATION_MAP = new HashMap<>();

    /** Map of contractions to their expanded forms (e.g., "can't" -> "cannot"). */
    private static final Map<String, String> CONTRACTION_MAP = new HashMap<>();

    /** Set of negative adverbs (e.g., "not", "never"). */
    private static final Set<String> NEGATIVE_ADVERBS = new HashSet<>();

    /** Precompiled pattern to detect sentence-ending punctuation. */
    private static final Pattern SENTENCE_ENDING_PUNCTUATION_PATTERN = Pattern.compile("[.!?]$");
    private static final Runnable DO_NOTHING = ()->{};

    static {
        // Initialize negation mappings (e.g., "can't" -> "can").
        NEGATION_MAP.put("can't", "can");
        NEGATION_MAP.put("cannot", "can");
        NEGATION_MAP.put("don't", "do");
        NEGATION_MAP.put("doesn't", "does");
        NEGATION_MAP.put("didn't", "did");
        NEGATION_MAP.put("won't", "will");
        NEGATION_MAP.put("wouldn't", "would");
        NEGATION_MAP.put("shouldn't", "should");
        NEGATION_MAP.put("couldn't", "could");
        NEGATION_MAP.put("isn't", "is");
        NEGATION_MAP.put("aren't", "are");
        NEGATION_MAP.put("wasn't", "was");
        NEGATION_MAP.put("weren't", "were");
        NEGATION_MAP.put("haven't", "have");
        NEGATION_MAP.put("hasn't", "has");
        NEGATION_MAP.put("hadn't", "had");
        NEGATION_MAP.put("n't", ""); // For cases like "couldn't" ➔ "could"

        // Map contractions to their full forms (e.g., "can't" -> "cannot").
        CONTRACTION_MAP.put("can't", "cannot");
        CONTRACTION_MAP.put("won't", "will not");
        CONTRACTION_MAP.put("n't", " not");
        CONTRACTION_MAP.put("'re", " are");
        CONTRACTION_MAP.put("'ve", " have");
        CONTRACTION_MAP.put("'d", " would");
        CONTRACTION_MAP.put("'ll", " will");
        CONTRACTION_MAP.put("'m", " am");

        // Initialize negative adverbs.
        NEGATIVE_ADVERBS.add("not");
        NEGATIVE_ADVERBS.add("never");
        NEGATIVE_ADVERBS.add("no");

        // Add negative adverbs to the adverb match set.
        ADVERB_MATCH_SET.addAll(NEGATIVE_ADVERBS);

        // Initialize modal verbs.
        MODAL_MATCH_SET.add("should");

        // Initialize conjunctions.
        CONJUNCTION_MATCH_SET.add("but");

        // Initialize pronoun mappings.
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

        // Initialize reflexive pronouns.
        REFLEXIVE_PRONOUNS.add("myself");
        REFLEXIVE_PRONOUNS.add("yourself");
        REFLEXIVE_PRONOUNS.add("himself");
        REFLEXIVE_PRONOUNS.add("herself");
        REFLEXIVE_PRONOUNS.add("itself");
        REFLEXIVE_PRONOUNS.add("ourselves");
        REFLEXIVE_PRONOUNS.add("yourselves");
        REFLEXIVE_PRONOUNS.add("themselves");

        // Initialize irregular past tense verbs.
        IRREGULAR_PAST_TENSE_VERBS.put("be", "was");
        IRREGULAR_PAST_TENSE_VERBS.put("begin", "began");
        IRREGULAR_PAST_TENSE_VERBS.put("break", "broke");
        IRREGULAR_PAST_TENSE_VERBS.put("bring", "brought");
        IRREGULAR_PAST_TENSE_VERBS.put("build", "built");
        IRREGULAR_PAST_TENSE_VERBS.put("buy", "bought");
        IRREGULAR_PAST_TENSE_VERBS.put("catch", "caught");
        IRREGULAR_PAST_TENSE_VERBS.put("choose", "chose");
        IRREGULAR_PAST_TENSE_VERBS.put("come", "came");
        IRREGULAR_PAST_TENSE_VERBS.put("do", "did");
        IRREGULAR_PAST_TENSE_VERBS.put("feel", "felt");
        IRREGULAR_PAST_TENSE_VERBS.put("find", "found");
        IRREGULAR_PAST_TENSE_VERBS.put("get", "got");
        IRREGULAR_PAST_TENSE_VERBS.put("go", "went");
        IRREGULAR_PAST_TENSE_VERBS.put("have", "had");
        IRREGULAR_PAST_TENSE_VERBS.put("hear", "heard");
        IRREGULAR_PAST_TENSE_VERBS.put("keep", "kept");
        IRREGULAR_PAST_TENSE_VERBS.put("know", "knew");
        IRREGULAR_PAST_TENSE_VERBS.put("leave", "left");
        IRREGULAR_PAST_TENSE_VERBS.put("make", "made");
        IRREGULAR_PAST_TENSE_VERBS.put("say", "said");
        IRREGULAR_PAST_TENSE_VERBS.put("see", "saw");
        IRREGULAR_PAST_TENSE_VERBS.put("take", "took");
        IRREGULAR_PAST_TENSE_VERBS.put("teach", "taught");
        IRREGULAR_PAST_TENSE_VERBS.put("think", "thought");
        IRREGULAR_PAST_TENSE_VERBS.put("write", "wrote");

        // Initialize irregular present tense verbs for third person singular.
        IRREGULAR_PRESENT_TENSE_VERBS.put("be", "is");
        IRREGULAR_PRESENT_TENSE_VERBS.put("have", "has");
        IRREGULAR_PRESENT_TENSE_VERBS.put("do", "does");
        IRREGULAR_PRESENT_TENSE_VERBS.put("go", "goes");
        IRREGULAR_PRESENT_TENSE_VERBS.put("say", "says");
        IRREGULAR_PRESENT_TENSE_VERBS.put("make", "makes");
        IRREGULAR_PRESENT_TENSE_VERBS.put("know", "knows");
        IRREGULAR_PRESENT_TENSE_VERBS.put("think", "thinks");
        IRREGULAR_PRESENT_TENSE_VERBS.put("take", "takes");
        IRREGULAR_PRESENT_TENSE_VERBS.put("see", "sees");
        IRREGULAR_PRESENT_TENSE_VERBS.put("come", "comes");
        IRREGULAR_PRESENT_TENSE_VERBS.put("get", "gets");
        IRREGULAR_PRESENT_TENSE_VERBS.put("feel", "feels");
        IRREGULAR_PRESENT_TENSE_VERBS.put("leave", "leaves");
        IRREGULAR_PRESENT_TENSE_VERBS.put("give", "gives");
        IRREGULAR_PRESENT_TENSE_VERBS.put("find", "finds");
        IRREGULAR_PRESENT_TENSE_VERBS.put("tell", "tells");
        IRREGULAR_PRESENT_TENSE_VERBS.put("become", "becomes");
        IRREGULAR_PRESENT_TENSE_VERBS.put("show", "shows");

        // Load profane words from 'profane_words.txt'.
        try (InputStream inputStream = NegativeSpeechDetector.class.getClassLoader().getResourceAsStream("profanity/profane_words.txt")) {
            if (inputStream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        PROFANITY_WORD_SET.add(line.trim().toLowerCase(Locale.ROOT));
                    }
                }
            } else {
                System.err.println("profane_words.txt not found in resources.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Executor for running tasks asynchronously. */
    public final TaskExecutor taskExecutor;

    /** Stanford CoreNLP pipeline for text annotation. */
    private final StanfordCoreNLP pipeline;

    /**
     * Constructs an {@code InjunctionDetector} with the specified {@link TaskExecutor}.
     *
     * @param taskExecutor The {@link TaskExecutor} to run tasks asynchronously.
     */
    public NegativeSpeechDetector(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;

        // Configure the Stanford CoreNLP pipeline with the desired annotators.
        Properties props = new Properties();

        // The 'annotators' property specifies the sequence of annotators to be applied to the text.
        // The annotators are applied in the order they are listed.
        // In this configuration, we include the following annotators:
        props.put("annotators", "tokenize, ssplit, pos, lemma, parse, depparse");

        /*
         * Explanation of each annotator:
         *
         * 1. tokenize:
         *    - Purpose:
         *      - Splits the input text into individual tokens (words, punctuation marks, etc.).
         *      - Tokenization is the fundamental first step in NLP preprocessing.
         *    - How it works:
         *      - Uses language-specific rules to handle different writing systems and punctuation.
         *    - Why it's needed:
         *      - Subsequent annotators (like POS tagging and parsing) operate on tokens.
         *      - Essential for any text processing tasks as it defines the units of analysis.
         *
         * 2. ssplit (Sentence Splitter):
         *    - Purpose:
         *      - Divides the text into sentences.
         *      - Identifies sentence boundaries based on punctuation and capitalization.
         *    - How it works:
         *      - Uses language-specific rules and machine learning models to detect sentence ends.
         *    - Why it's needed:
         *      - Many NLP tasks, such as parsing and coreference resolution, are performed at the sentence level.
         *      - Essential for correctly parsing and understanding the grammatical structure of the text.
         *
         * 3. pos (Part-of-Speech Tagger):
         *    - Purpose:
         *      - Assigns part-of-speech tags to each token (e.g., noun, verb, adjective).
         *      - Provides grammatical information about each word.
         *    - How it works:
         *      - Uses statistical models trained on annotated corpora to predict POS tags.
         *    - Why it's needed:
         *      - POS tags are crucial for syntactic parsing and understanding sentence structure.
         *      - In the InjunctionDetector, POS tags help identify injunctions by matching specific POS patterns (e.g., adverbs like "not").
         *      - Enables filtering or transforming text based on grammatical categories.
         *
         * 4. lemma (Lemmatization):
         *    - Purpose:
         *      - Reduces words to their base or dictionary form (lemma).
         *      - Normalizes different forms of a word to a single representation.
         *    - How it works:
         *      - Uses morphological analysis and dictionaries to map inflected forms to lemmas.
         *    - Why it's needed:
         *      - Essential for tasks like matching words against a profanity list regardless of tense or plurality.
         *      - Improves the accuracy of text analysis by considering word variants as the same token.
         *      - In the InjunctionDetector, lemmatization ensures that words like "running" and "ran" are recognized as "run".
         *
         * 5. parse (Constituency Parser):
         *    - Purpose:
         *      - Generates a constituency parse tree (phrase structure tree) for each sentence.
         *      - Represents the hierarchical structure of sentences in terms of nested constituents (phrases).
         *    - How it works:
         *      - Uses probabilistic context-free grammars (PCFG) to parse sentences.
         *    - Why it's needed:
         *      - Provides detailed syntactic information about the sentence structure.
         *      - In the InjunctionDetector, it's used to:
         *        - Reconstruct sentences in methods like `rewriteNotOnlyButAlsoSentenceUsingParseTree()`.
         *        - Handle complex sentence transformations that rely on understanding phrase boundaries.
         *      - Helps in disambiguating syntactic ambiguities in sentences.
         *
         * 6. depparse (Dependency Parser):
         *    - Purpose:
         *      - Generates a dependency parse tree for each sentence.
         *      - Represents grammatical relations between words (e.g., subject, object, modifiers).
         *    - How it works:
         *      - Uses models trained on dependency treebanks to predict relations between words.
         *    - Why it's needed:
         *      - Essential for understanding the functional relationships in a sentence.
         *      - In the InjunctionDetector, dependency parsing is used to:
         *        - Identify grammatical roles such as subject (`nsubj`), object (`dobj`), and complement verbs (`xcomp`, `ccomp`).
         *        - Perform transformations in methods like `transformSentenceInternal()` by locating specific grammatical patterns.
         *      - Enables accurate manipulation of sentence components based on their syntactic roles.
         *
         * Rationale for including these annotators:
         * - The combination of these annotators provides a comprehensive linguistic analysis of the text.
         * - They enable the InjunctionDetector to:
         *   - Detect complex linguistic patterns and injunctions in the text.
         *   - Perform sophisticated sentence transformations while maintaining grammatical correctness.
         *   - Identify and process parts of the text based on grammatical categories and relationships.
         * - The order of annotators ensures that each step builds upon the previous ones:
         *   - Tokenization and sentence splitting prepare the text for analysis.
         *   - POS tagging and lemmatization add grammatical information to tokens.
         *   - Parsing annotators (parse and depparse) provide deep syntactic structures necessary for advanced NLP tasks.
         */

        pipeline = new StanfordCoreNLP(props);
    }

    /**
     * Detects if the sentence ends with proper punctuation using a regex pattern.
     *
     * This method checks if the input sentence ends with a sentence-ending punctuation mark
     * (e.g., ".", "!", "?") using a precompiled regex pattern.
     *
     * @param sentence The sentence to check.
     * @return True if the sentence ends with appropriate punctuation; false otherwise.
     */
    private boolean endsWithPunctuation(String sentence) {
        if (sentence == null || sentence.isEmpty()) {
            return false;
        }

        // Trim any trailing whitespace
        sentence = sentence.trim();

        // Use the precompiled regex pattern to check for sentence-ending punctuation
        return SENTENCE_ENDING_PUNCTUATION_PATTERN.matcher(sentence).find();
    }


    /**
     * Suggests an improved sentence if it matches specific patterns.
     *
     * <p>Currently, this method focuses on sentences containing the "not only ... but also ..." pattern,
     * and rewrites them using "both ... and ..." for a more concise expression.
     *
     * <p><b>Example:</b>
     * <pre>
     * Original: "She is not only smart but also kind."
     * Suggested: "She is both smart and kind."
     * </pre>
     *
     * @param message The original message.
     * @return The improved sentence if applicable; otherwise, the original message.
     */
    public String suggestImprovedSentence(final String message) {
        if (detectNotOnlyButAlsoPattern(message)) {
            // Use parse tree to reconstruct the sentence
            return rewriteNotOnlyButAlsoSentenceUsingParseTree(message);
        }
        // Return the original message if no improvement is needed
        return message;
    }

    /**
     * Asynchronously checks if the given message contains an injunction.
     *
     * This method leverages the TaskExecutor to perform the injunction detection
     * in a separate thread, allowing the main thread to continue without blocking.
     *
     * @param message  The message to analyze.
     * @param callback A Consumer<Boolean> callback to receive the result.
     */
    public void isInjunctionAsync(final String message, Consumer<Boolean> callback) {
        // Execute the injunction detection in a separate thread
        taskExecutor.execute(() -> {
            // Perform the synchronous injunction check
            boolean result = isInjunction(message);
            // Pass the result to the callback
            callback.accept(result);
        });
    }

    /**
     * Checks if the given message contains an injunction.
     *
     * <p>An injunction is detected if the message contains negations, negative adverbs,
     * specific modal verbs, or certain conjunctions.
     *
     * <p><b>Example:</b>
     * <pre>
     * Message: "You shouldn't do that."
     * Returns: true
     * </pre>
     *
     * @param message The message to analyze.
     * @return {@code true} if an injunction is detected; {@code false} otherwise.
     */
    public boolean isInjunction(final String message) {
        // First, check for the specific pattern "not only ... but also ..."
        if (detectNotOnlyButAlsoPattern(message)) {
            return true;
        }

        // Annotate the message
        Annotation document = new Annotation(message);
        pipeline.annotate(document);
        List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);

        for (CoreLabel token : tokens) {
            String posTag = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
            String word = token.originalText().toLowerCase(Locale.ROOT);
            String lemma = token.get(CoreAnnotations.LemmaAnnotation.class).toLowerCase(Locale.ROOT);

            // Check for negative contractions and negative adverbs
            if (NEGATION_MAP.containsKey(word) || NEGATIVE_ADVERBS.contains(word) || NEGATIVE_ADVERBS.contains(lemma)) {
                return true;
            }

            // Additionally, check if the token ends with "n't" (e.g., "didn't", "couldn't")
            if (word.endsWith("n't") || word.endsWith("n’t")) {
                return true;
            }

            switch (posTag) {
                case POS_ADVERB:
                    // Check if the adverb matches any in the ADVERB_MATCH_SET
                    if (ADVERB_MATCH_SET.contains(word) || ADVERB_MATCH_SET.contains(lemma)) {
                        return true;
                    }
                    break;
                case POS_MODAL:
                    // Check if the modal verb matches any in the MODAL_MATCH_SET
                    if (MODAL_MATCH_SET.contains(word) || MODAL_MATCH_SET.contains(lemma)) {
                        return true;
                    }
                    break;
                case POS_CONJUNCTION:
                    // Check if the conjunction matches any in the CONJUNCTION_MATCH_SET
                    if (CONJUNCTION_MATCH_SET.contains(word) || CONJUNCTION_MATCH_SET.contains(lemma)) {
                        return true;
                    }
                    break;
            }
        }
        return false;
    }

    /**
     * Asynchronously checks if the given message contains profanity.
     *
     * @param message  The message to check.
     * @param callback The callback to receive the result.
     */
    public void containsProfanityAsync(final String message, Consumer<Boolean> callback) {
        taskExecutor.execute(() -> {
            boolean result = containsProfanity(message);
            callback.accept(result);
        });
    }


    /**
     * Checks if the given message contains profanity.
     *
     * <p>This method analyzes the lemmatized tokens of the message and compares them
     * against a predefined set of profane words.
     *
     * <p><b>Example:</b>
     * <pre>
     * Message: "That was a damn mistake."
     * Returns: true
     * </pre>
     *
     * @param message The message to check.
     * @return {@code true} if profanity is detected; {@code false} otherwise.
     */
    public boolean containsProfanity(final String message) {
        Annotation document = new Annotation(message);
        pipeline.annotate(document);
        List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);

        for (CoreLabel token : tokens) {
            String lemma = token.get(CoreAnnotations.LemmaAnnotation.class).toLowerCase(Locale.ROOT);
            if (PROFANITY_WORD_SET.contains(lemma)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Asynchronously suggests an improved sentence if applicable.
     *
     * @param message  The original message.
     * @param callback The callback to receive the improved sentence.
     */
    public void suggestImprovedSentenceAsync(final String message, Consumer<String> callback) {
        taskExecutor.execute(() -> {
            String improved = suggestImprovedSentence(message);
            callback.accept(improved);
        });
    }

    /**
     * Asynchronously suggests a replacement for sentences containing injunctions.
     *
     * @param message  The original message.
     * @param callback The callback to receive the replacement sentence.
     */
    public void suggestInjunctionReplacementAsync(final String message, Consumer<String> callback) {
        taskExecutor.execute(() -> {
            String replacement = suggestInjunctionReplacement(message);
            callback.accept(replacement);
        });
    }

    /**
     * Suggests a replacement for sentences containing injunctions by removing negations.
     *
     * @param message The original message.
     * @return The replacement sentence if an injunction is detected; otherwise, returns the original message.
     */
    public String suggestInjunctionReplacement(final String message) {
        Annotation document = new Annotation(message);
        pipeline.annotate(document);

        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        StringBuilder result = new StringBuilder();

        boolean injunctionFound = false;

        for (CoreMap sentence : sentences) {
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            StringBuilder sentenceBuilder = new StringBuilder();

            for (int i = 0; i < tokens.size(); i++) {
                CoreLabel token = tokens.get(i);
                String word = token.originalText();
                String lowerWord = word.toLowerCase(Locale.ROOT);

                if (NEGATION_MAP.containsKey(lowerWord)) {
                    // Replace negative verb with its positive form
                    String replacement = NEGATION_MAP.get(lowerWord);
                    sentenceBuilder.append(replacement);
                    injunctionFound = true;
                } else if (NEGATIVE_ADVERBS.contains(lowerWord)) {
                    // Skip the negative adverb
                    injunctionFound = true;
                    continue;
                } else {
                    // Keep the original word
                    sentenceBuilder.append(word);
                }
                // Append whitespace
                sentenceBuilder.append(token.after());
            }
            String modifiedSentence = sentenceBuilder.toString().trim();
            result.append(modifiedSentence);
            result.append(" ");
        }

        if (injunctionFound) {
            // Return the modified sentence
            String replacementSentence = result.toString().trim();
            // Capitalize the first letter
            replacementSentence = capitalizeFirstLetter(replacementSentence);
            // We are not adding any punctuation here, per your request
            return replacementSentence;
        } else {
            // Return the original message if no injunction is found
            return message;
        }
    }

    /**
     * Asynchronously suggests a replacement for sentences containing conjunctions like "but".
     *
     * @param message  The original message.
     * @param callback The callback to receive the replacement sentence.
     */
    public void suggestConjunctionReplacementAsync(final String message, Consumer<String> callback) {
        taskExecutor.execute(() -> {
            String replacement = suggestConjunctionReplacement(message);
            callback.accept(replacement);
        });
    }

    /**
     * Suggests a replacement for sentences containing conjunctions by replacing "but" with "and".
     *
     * @param message The original message.
     * @return The replacement sentence if a conjunction is detected; otherwise, returns the original message.
     */
    public String suggestConjunctionReplacement(final String message) {
        Annotation document = new Annotation(message);
        pipeline.annotate(document);

        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        StringBuilder result = new StringBuilder();

        boolean conjunctionFound = false;

        for (CoreMap sentence : sentences) {
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            StringBuilder sentenceBuilder = new StringBuilder();

            for (CoreLabel token : tokens) {
                String word = token.originalText();
                String lowerWord = word.toLowerCase(Locale.ROOT);

                if (CONJUNCTION_MATCH_SET.contains(lowerWord)) {
                    // Replace "but" with "and"
                    sentenceBuilder.append("and");
                    conjunctionFound = true;
                } else {
                    // Keep the original word
                    sentenceBuilder.append(word);
                }
                // Append whitespace
                sentenceBuilder.append(token.after());
            }
            String modifiedSentence = sentenceBuilder.toString().trim();
            result.append(modifiedSentence);
            result.append(" ");
        }

        if (conjunctionFound) {
            // Return the modified sentence
            String replacementSentence = result.toString().trim();
            // Capitalize the first letter
            replacementSentence = capitalizeFirstLetter(replacementSentence);
            // We are not adding any punctuation here, per your request
            return replacementSentence;
        } else {
            // Return the original message if no conjunction is found
            return message;
        }
    }
    public boolean hasConjunction(final String message) {
        Annotation document = new Annotation(message);
        pipeline.annotate(document);

        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        StringBuilder result = new StringBuilder();

        boolean conjunctionFound = false;

        for (CoreMap sentence : sentences) {
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            StringBuilder sentenceBuilder = new StringBuilder();

            for (CoreLabel token : tokens) {
                String word = token.originalText();
                String lowerWord = word.toLowerCase(Locale.ROOT);

                if (CONJUNCTION_MATCH_SET.contains(lowerWord)) {
                    // Replace "but" with "and"
                    sentenceBuilder.append("and");
                    return true;
                } else {
                    // Keep the original word
                    sentenceBuilder.append(word);
                }
                // Append whitespace
                sentenceBuilder.append(token.after());
            }
            String modifiedSentence = sentenceBuilder.toString().trim();
            result.append(modifiedSentence);
            result.append(" ");
        }
        return false;
    }

    /**
     * Transforms a sentence based on specific linguistic patterns.
     *
     * <p>This method is the asynchronous counterpart to {@code transformSentenceInternal},
     * allowing the transformation to be executed without blocking the main thread.
     *
     * @param sentence The original sentence.
     * @param callback A {@link Consumer} to handle the transformed sentence.
     */
    public void transformSentence(String sentence, Consumer<String> callback) {
        taskExecutor.execute(() -> {
            String transformed = transformSentenceInternal(sentence);
            callback.accept(transformed);
        });
    }

    /**
     * Transforms the sentence internally.
     *
     * @param sentence The original sentence.
     * @return The transformed sentence.
     */
    private String transformSentenceInternal(final String sentence) {
        // Annotate the sentence using Stanford CoreNLP.
        Annotation document = new Annotation(sentence);
        pipeline.annotate(document);

        // Get the list of sentences (assuming the input is a single sentence).
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        if (sentences.isEmpty()) {
            return sentence; // Return the original sentence if no sentences are found.
        }

        CoreMap cmSentence = sentences.get(0);

        // Obtain the dependency parse of the sentence.
        SemanticGraph dependencies = cmSentence.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);

        // Identify the main verb (root of the dependency tree).
        IndexedWord mainVerb = dependencies.getFirstRoot();
        if (mainVerb == null) {
            return sentence;
        }

        // Get the POS tag and lemma of the main verb.
        String mainVerbPOS = mainVerb.get(CoreAnnotations.PartOfSpeechAnnotation.class);
        String mainVerbLemma = mainVerb.get(CoreAnnotations.LemmaAnnotation.class);

        // Initialize variables to hold grammatical components.
        IndexedWord mainVerbSubject = null;          // The subject of the main verb.
        IndexedWord mainVerbObject = null;           // The object of the main verb.
        IndexedWord complementVerb = null;           // The complement verb (e.g., "feel" in "make me feel").
        IndexedWord complementVerbSubject = null;    // The subject of the complement verb.
        String modalVerb = "";                       // Modal verb associated with the main verb (e.g., "will").
        boolean isMainVerbNegated = false;           // Whether the main verb is negated.
        boolean isComplementVerbInfinitive = false;  // Whether the complement verb is an infinitive.

        // Iterate over the dependency edges to find grammatical relations.
        for (SemanticGraphEdge edge : dependencies.edgeIterable()) {
            GrammaticalRelation reln = edge.getRelation();
            IndexedWord governor = edge.getGovernor();
            IndexedWord dependent = edge.getDependent();

            // Find the nominal subject (nsubj) of the main verb.
            if (reln.getShortName().equals("nsubj") && governor.equals(mainVerb)) {
                mainVerbSubject = dependent;
            }
            // Find the object (dobj or iobj) of the main verb.
            else if ((reln.getShortName().equals("dobj") || reln.getShortName().equals("iobj")) && governor.equals(mainVerb)) {
                mainVerbObject = dependent;
            }
            // Find the complement verb (xcomp or ccomp) of the main verb.
            else if ((reln.getShortName().equals("xcomp") || reln.getShortName().equals("ccomp")) && governor.equals(mainVerb)) {
                complementVerb = dependent;
            }
            // Find modal verbs.
            else if (reln.getShortName().equals("aux") && dependent.get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("MD") && governor.equals(mainVerb)) {
                modalVerb = dependent.originalText().toLowerCase(Locale.ROOT);
            }
            // Check for negation modifiers on the main verb or its auxiliaries.
            else if (reln.getShortName().equals("neg") && (governor.equals(mainVerb) || dependencies.getShortestUndirectedPathNodes(mainVerb, governor) != null)) {
                isMainVerbNegated = true;
            }
            // Check for "to" before the complement verb (infinitive marker).
            else if (reln.getShortName().equals("mark") && governor.equals(complementVerb) && dependent.originalText().equalsIgnoreCase("to")) {
                isComplementVerbInfinitive = true;
            }
        }

        if (complementVerb != null) {
            // Find the subject of the complement verb.
            for (SemanticGraphEdge edge : dependencies.outgoingEdgeList(complementVerb)) {
                GrammaticalRelation reln = edge.getRelation();
                IndexedWord dependent = edge.getDependent();

                if (reln.getShortName().equals("nsubj") || reln.getShortName().equals("nsubj:xsubj")) {
                    complementVerbSubject = dependent;
                    break;
                }
            }
        }

        // If the subject of the complement verb is not found, it may be controlled by the object of the main verb.
        if (complementVerbSubject == null && mainVerbObject != null) {
            complementVerbSubject = mainVerbObject;
        }

        // Check if all necessary components are found.
        if (mainVerbSubject != null && complementVerbSubject != null && complementVerb != null) {
            // Swap pronouns to keep consistency.
            String newSubject = OBJECT_TO_SUBJECT_PRONOUN.getOrDefault(complementVerbSubject.originalText().toLowerCase(Locale.ROOT), complementVerbSubject.originalText());
            String newObject = SUBJECT_TO_OBJECT_PRONOUN.getOrDefault(mainVerbSubject.originalText().toLowerCase(Locale.ROOT), mainVerbSubject.originalText());

            // Capitalize the new subject.
            newSubject = capitalizeFirstLetter(newSubject);

            // Handle reflexive pronouns.
            if (isReflexivePronoun(complementVerbSubject.originalText())) {
                return sentence;
            }

            // Extract the complement phrase.
            Set<IndexedWord> descendants = dependencies.getSubgraphVertices(complementVerb);
            descendants.add(complementVerb);

            // Remove duplicates and sort the words according to their positions in the sentence.
            List<IndexedWord> complementWords = new ArrayList<>(descendants);
            complementWords.sort(Comparator.comparingInt(IndexedWord::index));

            // Create a set of words to exclude.
            Set<IndexedWord> wordsToExclude = new HashSet<>();
            wordsToExclude.add(complementVerbSubject);
            // Also add "to" if present.
            for (IndexedWord word : dependencies.vertexSet()) {
                if (word.originalText().equalsIgnoreCase("to")) {
                    wordsToExclude.add(word);
                    break;
                }
            }

            // Remove the complement verb subject and "to" from the complementWords.
            complementWords.removeIf(word -> wordsToExclude.contains(word));

            // Build the complement phrase.
            StringBuilder complementBuilder = new StringBuilder();
            for (IndexedWord word : complementWords) {
                complementBuilder.append(word.originalText());
                complementBuilder.append(word.after());
            }
            String complement = complementBuilder.toString().trim();

            // Adjust the complement verb tense.
            String complementVerbLemma = complementVerb.get(CoreAnnotations.LemmaAnnotation.class);
            String adjustedComplementVerb = adjustVerbForm(complementVerbLemma, mainVerbPOS, modalVerb, isComplementVerbInfinitive, newSubject);

            // Replace the complement verb in the complement phrase.
            complement = complement.replaceFirst("\\b" + Pattern.quote(complementVerb.originalText()) + "\\b", adjustedComplementVerb);

            // Include additional modifiers or clauses attached to the complement verb.
            String additionalPhrase = extractAdditionalPhrases(dependencies, complementVerb, complementWords, wordsToExclude);

            // Reconstruct the transformed sentence.
            StringBuilder transformedSentenceBuilder = new StringBuilder();
            transformedSentenceBuilder.append(newSubject).append(" ");

            // Include modal if present.
            if (!modalVerb.isEmpty()) {
                transformedSentenceBuilder.append(modalVerb).append(" ");
            }

            // Include negation if the main verb or its auxiliaries are negated.
            if (isMainVerbNegated) {
                transformedSentenceBuilder.append("not ");
            }

            transformedSentenceBuilder.append(complement);

            if (!additionalPhrase.isEmpty()) {
                transformedSentenceBuilder.append(" ").append(additionalPhrase);
            }

            // Choose appropriate preposition.
            String preposition = choosePreposition(complementVerbLemma, isComplementVerbInfinitive);

            transformedSentenceBuilder.append(" ").append(preposition).append(" ").append(newObject).append(".");

            String transformedSentence = transformedSentenceBuilder.toString();

            return transformedSentence;
        }

        // Return the original sentence if the pattern is not matched.
        return sentence;
    }

    /**
     * Adjusts the verb form based on tense, modality, and infinitive.
     *
     * <p>This method conjugates the verb appropriately for the new subject and tense.
     *
     * @param verbLemma The base form of the verb.
     * @param mainVerbPOS The part-of-speech tag of the main verb.
     * @param modalVerb The modal verb associated with the main verb.
     * @param isInfinitive Whether the verb is in infinitive form.
     * @param subject The new subject of the verb.
     * @return The adjusted verb form.
     */
    private String adjustVerbForm(String verbLemma, String mainVerbPOS, String modalVerb, boolean isInfinitive, String subject) {
        // Handle infinitive "to be".
        if (isInfinitive && verbLemma.equals("be")) {
            if (!modalVerb.isEmpty()) {
                return verbLemma;
            } else if (mainVerbPOS.startsWith("VBD")) {
                return subject.equalsIgnoreCase("I") ? "was" : "were";
            } else {
                return subject.equalsIgnoreCase("he") || subject.equalsIgnoreCase("she") || subject.equalsIgnoreCase("it") ? "is" : "are";
            }
        }

        // Handle other verbs.
        if (modalVerb.equals("will")) {
            return verbLemma;
        } else if (mainVerbPOS.startsWith("VBD")) {
            return getPastTense(verbLemma);
        } else if (mainVerbPOS.startsWith("VB")) {
            return getPresentTense(verbLemma, subject);
        }

        return verbLemma;
    }

    /**
     * Extracts additional phrases attached to the complement verb, excluding specified words.
     *
     * <p>This method performs a breadth-first traversal starting from the complement verb,
     * collecting all connected words while avoiding cycles and excluding specified words.
     *
     * @param dependencies The dependency graph of the sentence.
     * @param complementVerb The complement verb node.
     * @param complementWords The list of words already included in the complement phrase.
     * @param wordsToExclude A set of words to exclude from the additional phrases.
     * @return A string containing the additional phrases.
     */
    private String extractAdditionalPhrases(SemanticGraph dependencies, IndexedWord complementVerb, List<IndexedWord> complementWords, Set<IndexedWord> wordsToExclude) {
        List<IndexedWord> additionalWords = new ArrayList<>();

        Queue<IndexedWord> queue = new LinkedList<>();
        Set<IndexedWord> visited = new HashSet<>();
        queue.add(complementVerb);

        while (!queue.isEmpty()) {
            IndexedWord current = queue.poll();
            visited.add(current);

            for (SemanticGraphEdge edge : dependencies.outgoingEdgeList(current)) {
                IndexedWord dependent = edge.getDependent();
                if (!complementWords.contains(dependent) && !wordsToExclude.contains(dependent) && !visited.contains(dependent)) {
                    additionalWords.add(dependent);
                    queue.add(dependent);
                }
            }
        }

        // Remove duplicates and sort.
        Set<IndexedWord> uniqueAdditionalWords = new HashSet<>(additionalWords);
        additionalWords = new ArrayList<>(uniqueAdditionalWords);
        additionalWords.sort(Comparator.comparingInt(IndexedWord::index));

        StringBuilder additionalBuilder = new StringBuilder();
        for (IndexedWord word : additionalWords) {
            additionalBuilder.append(word.originalText()).append(word.after());
        }

        return additionalBuilder.toString().trim();
    }

    /**
     * Checks if a word is a reflexive pronoun.
     *
     * @param word The word to check.
     * @return True if the word is a reflexive pronoun; false otherwise.
     */
    private boolean isReflexivePronoun(String word) {
        return REFLEXIVE_PRONOUNS.contains(word.toLowerCase(Locale.ROOT));
    }

    /**
     * Checks if a character is a vowel.
     *
     * @param c The character to check.
     * @return True if the character is a vowel; false otherwise.
     */
    private boolean isVowel(char c) {
        return "aeiou".indexOf(Character.toLowerCase(c)) != -1;
    }

    /**
     * Chooses the appropriate preposition based on context.
     */
    private String choosePreposition(String verbLemma, boolean isInfinitive) {
        if (verbLemma.equals("be") && isInfinitive) {
            return "of";
        }
        return "with";
    }


    /**
     * Gets the past tense form of a verb.
     *
     * <p>This method handles both irregular and regular verbs to generate the past tense form.
     *
     * <p><b>Examples:</b>
     * <pre>
     * getPastTense("go")    ➔ "went"  (irregular)
     * getPastTense("walk")  ➔ "walked" (regular)
     * </pre>
     *
     * @param verbLemma The base form (lemma) of the verb.
     * @return The past tense form of the verb.
     */
    private String getPastTense(String verbLemma) {
        if (IRREGULAR_PAST_TENSE_VERBS.containsKey(verbLemma)) {
            return IRREGULAR_PAST_TENSE_VERBS.get(verbLemma);
        }
        // Handle regular verbs
        if (verbLemma.endsWith("e")) {
            return verbLemma + "d";
        } else if (verbLemma.endsWith("y") && !isVowel(verbLemma.charAt(verbLemma.length() - 2))) {
            // For verbs ending with consonant + y (e.g., "try" ➔ "tried")
            return verbLemma.substring(0, verbLemma.length() - 1) + "ied";
        } else {
            // For other regular verbs
            return verbLemma + "ed";
        }
    }

    /**
     * Gets the present tense form of a verb for third person singular subjects.
     *
     * <p>This method handles both irregular and regular verbs to generate the correct
     * present tense form for subjects like "he", "she", or "it".
     *
     * <p><b>Examples:</b>
     * <pre>
     * getPresentTense("have", "he")  ➔ "has"    (irregular)
     * getPresentTense("play", "she") ➔ "plays"  (regular)
     * </pre>
     *
     * @param verbLemma The base form (lemma) of the verb.
     * @param subject   The subject of the verb.
     * @return The present tense form of the verb.
     */
    private String getPresentTense(String verbLemma, String subject) {
        String lowerSubject = subject.toLowerCase(Locale.ROOT);
        // For third person singular subjects (he, she, it)
        if (lowerSubject.equals("he") || lowerSubject.equals("she") || lowerSubject.equals("it")) {
            if (IRREGULAR_PRESENT_TENSE_VERBS.containsKey(verbLemma)) {
                return IRREGULAR_PRESENT_TENSE_VERBS.get(verbLemma);
            }
            // Handle regular verbs
            if (verbLemma.endsWith("y") && !isVowel(verbLemma.charAt(verbLemma.length() - 2))) {
                // For verbs ending with consonant + y (e.g., "study" ➔ "studies")
                return verbLemma.substring(0, verbLemma.length() - 1) + "ies";
            } else if (verbLemma.endsWith("s") || verbLemma.endsWith("sh") || verbLemma.endsWith("ch")
                    || verbLemma.endsWith("x") || verbLemma.endsWith("z")) {
                // For verbs ending with sibilant sounds (e.g., "watch" ➔ "watches")
                return verbLemma + "es";
            } else {
                // For other regular verbs (e.g., "play" ➔ "plays")
                return verbLemma + "s";
            }
        } else {
            // For other subjects, the base form is used
            return verbLemma;
        }
    }

    /**
     * Detects if the sentence contains the pattern "not only ... but also ...".
     *
     * @param message The sentence to check.
     * @return True if the pattern is detected; false otherwise.
     */
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

    /**
     * Helper method to check if the next word matches the expected word.
     *
     * @param tokens          The list of tokens.
     * @param currentIndex    The current index.
     * @param expectedNextWord The expected next word.
     * @return True if the next word matches; false otherwise.
     */
    private boolean hasNextWord(List<CoreLabel> tokens, int currentIndex, String expectedNextWord) {
        return currentIndex + 1 < tokens.size() &&
                tokens.get(currentIndex + 1).originalText().toLowerCase(Locale.ROOT).equals(expectedNextWord);
    }

    /**
     * Rewrites the sentence by replacing "not only ... but also ..." with "both ... and ...".
     *
     * @param message The original sentence.
     * @return The rewritten sentence.
     */
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

        // Find indices of "not only" and "but also"
        for (int i = 0; i < tokens.size(); i++) {
            String word = tokens.get(i).originalText().toLowerCase(Locale.ROOT);
            if (word.equals("not") && hasNextWord(tokens, i, "only")) {
                notOnlyIndex = i;
            }
            if (word.equals("but") && hasNextWord(tokens, i, "also")) {
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

        // Ensure proper punctuation
        String improvedText = improvedSentence.toString().trim();
        if (!improvedText.endsWith(".") && !improvedText.endsWith("!") && !improvedText.endsWith("?")) {
            improvedText += ".";
        }

        return improvedText;
    }

    /**
     * Extracts a phrase from the tokens between the specified indices.
     *
     * @param tokens     The list of tokens.
     * @param startIndex The start index (inclusive).
     * @param endIndex   The end index (exclusive).
     * @return The extracted phrase.
     */
    private String extractPhrase(List<CoreLabel> tokens, int startIndex, int endIndex) {
        StringBuilder phrase = new StringBuilder();
        for (int i = startIndex; i < endIndex && i < tokens.size(); i++) {
            phrase.append(tokens.get(i).originalText());
            phrase.append(tokens.get(i).after()); // Preserve whitespace
        }
        return phrase.toString();
    }

    /**
     * Capitalizes the first letter of a sentence.
     *
     * @param sentence The sentence to capitalize.
     * @return The sentence with the first letter capitalized.
     */
    private String capitalizeFirstLetter(String sentence) {
        if (sentence == null || sentence.isEmpty()) {
            return sentence;
        }
        return sentence.substring(0, 1).toUpperCase() + sentence.substring(1);
    }

    // New method to extract complete sentences
    public SentenceExtractionResult extractCompleteSentences(String text) {
        if (text == null || text.isEmpty()) {
            return new SentenceExtractionResult(Collections.emptyList(), "");
        }

        Annotation document = new Annotation(text);
        pipeline.annotate(document);

        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        if (sentences == null || sentences.isEmpty()) {
            // No complete sentences found; keep the text in the buffer
            return new SentenceExtractionResult(Collections.emptyList(), text);
        }

        List<String> sentenceTexts = new ArrayList<>();
        int lastSentenceEnd = 0;

        for (CoreMap sentence : sentences) {
            int beginOffset = sentence.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
            int endOffset = sentence.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);

            String sentenceText = sentence.get(CoreAnnotations.TextAnnotation.class).trim();

            // Check if the sentence ends with sentence-ending punctuation
            if (endsWithSentenceEndingPunctuation(sentenceText)) {
                sentenceTexts.add(sentenceText);
                lastSentenceEnd = endOffset;
            } else {
                // Incomplete sentence; stop processing further sentences
                break;
            }
        }

        String remainingText = "";
        if (lastSentenceEnd < text.length()) {
            remainingText = text.substring(lastSentenceEnd);
        }

        return new SentenceExtractionResult(sentenceTexts, remainingText);
    }

    // Helper method to check for sentence-ending punctuation
    private boolean endsWithSentenceEndingPunctuation(String text) {
        return text.endsWith(".") || text.endsWith("!") || text.endsWith("?");
    }

    public void needsReplacement(final String sentence, final Runnable ifNeeded) {
        AtomicReference<Runnable> once = new AtomicReference<>(ifNeeded);
        taskExecutor.execute(() -> {
            if (isInjunction(sentence)) {
                once.getAndSet(DO_NOTHING).run();
            }
        });
        taskExecutor.execute(() -> {
            if (detectNotOnlyButAlsoPattern(sentence)) {
                once.getAndSet(DO_NOTHING).run();
            }
        });
        taskExecutor.execute(() -> {
            if (hasConjunction(sentence)) {
                once.getAndSet(DO_NOTHING).run();
            }
        });
    }
}
