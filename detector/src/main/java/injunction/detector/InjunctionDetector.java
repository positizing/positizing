package injunction.detector;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * InjunctionDetector:
 *
 * A class that detects injunctions, profane words, and transforms sentences
 * based on specified linguistic patterns using Stanford CoreNLP.
 *
 * It also provides replacement suggestions for sentences containing injunctions and conjunctions.
 *
 * All processing is done asynchronously using a TaskExecutor.
 *
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
    private static final Set<String> REFLEXIVE_PRONOUNS = new HashSet<>();

    private static final Map<String, String> SUBJECT_TO_OBJECT_PRONOUN = new HashMap<>();
    private static final Map<String, String> OBJECT_TO_SUBJECT_PRONOUN = new HashMap<>();

    private static final Map<String, String> NEGATION_MAP = new HashMap<>();
    private static final Map<String, String> CONTRACTION_MAP = new HashMap<>();
    private static final Set<String> NEGATIVE_ADVERBS = new HashSet<>();

    // Precompiled pattern to detect sentence-ending punctuation
    private static final Pattern SENTENCE_ENDING_PUNCTUATION_PATTERN = Pattern.compile("[.!?]$");

    static {
        // Initialize negation maps
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

        // Map contractions to full forms
        CONTRACTION_MAP.put("can't", "cannot");
        CONTRACTION_MAP.put("won't", "will not");
        CONTRACTION_MAP.put("n't", " not");
        CONTRACTION_MAP.put("'re", " are");
        CONTRACTION_MAP.put("'ve", " have");
        CONTRACTION_MAP.put("'d", " would");
        CONTRACTION_MAP.put("'ll", " will");
        CONTRACTION_MAP.put("'m", " am");

        // Negative adverbs
        NEGATIVE_ADVERBS.add("not");
        NEGATIVE_ADVERBS.add("never");
        NEGATIVE_ADVERBS.add("no");

        ADVERB_MATCH_LIST.addAll(NEGATIVE_ADVERBS);

        MODAL_MATCH_LIST.add("should");

        // Initialize conjunction match list
        CONJUNCTION_MATCH_LIST.add("but");

        // Initialize pronoun mappings
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

        // Initialize reflexive pronouns
        REFLEXIVE_PRONOUNS.add("myself");
        REFLEXIVE_PRONOUNS.add("yourself");
        REFLEXIVE_PRONOUNS.add("himself");
        REFLEXIVE_PRONOUNS.add("herself");
        REFLEXIVE_PRONOUNS.add("itself");
        REFLEXIVE_PRONOUNS.add("ourselves");
        REFLEXIVE_PRONOUNS.add("yourselves");
        REFLEXIVE_PRONOUNS.add("themselves");

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

    private final TaskExecutor taskExecutor;
    private final StanfordCoreNLP pipeline;

    /**
     * Constructs an InjunctionDetector with the specified TaskExecutor.
     *
     * @param taskExecutor The TaskExecutor to run tasks asynchronously.
     */
    public InjunctionDetector(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;

        // Configure the Stanford CoreNLP pipeline with the desired annotators
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
     * This method performs a synchronous analysis of the input message to determine
     * if it contains any injunctions based on predefined patterns and linguistic features.
     *
     * @param message The message to analyze.
     * @return True if an injunction is detected; false otherwise.
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
            String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
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

            switch (pos) {
                case TYPE_ADVERB:
                    // Check if the adverb matches any in the ADVERB_MATCH_LIST
                    if (ADVERB_MATCH_LIST.contains(word) || ADVERB_MATCH_LIST.contains(lemma)) {
                        return true;
                    }
                    break;
                case TYPE_MODAL:
                    // Check if the modal verb matches any in the MODAL_MATCH_LIST
                    if (MODAL_MATCH_LIST.contains(word) || MODAL_MATCH_LIST.contains(lemma)) {
                        return true;
                    }
                    break;
                case TYPE_CONJUNCTION:
                    // Check if the conjunction matches any in the CONJUNCTION_MATCH_LIST
                    if (CONJUNCTION_MATCH_LIST.contains(word) || CONJUNCTION_MATCH_LIST.contains(lemma)) {
                        return true;
                    }
                    break;
            }
        }
        return false;
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
     * @param message The message to check.
     * @return True if profanity is detected; false otherwise.
     */
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

                if (CONJUNCTION_MATCH_LIST.contains(lowerWord)) {
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

    /**
     * Asynchronously transforms sentences based on specified patterns.
     *
     * @param sentence The input sentence to transform.
     * @param callback The callback to receive the transformed sentence.
     */
    public void transformSentence(final String sentence, Consumer<String> callback) {
        taskExecutor.execute(() -> {
            String transformed = transformSentenceInternal(sentence);
            callback.accept(transformed);
        });
    }

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
        if (rootVerb == null) {
            return sentence;
        }

        // Initialize variables to hold grammatical components
        IndexedWord subject1 = null;        // The subject of the main verb (e.g., "She")
        IndexedWord object = null;          // The object of the main verb (e.g., "me")
        IndexedWord complementVerb = null;  // The complement verb (e.g., "feel", "learn")
        IndexedWord subject2 = null;        // The subject of the complement verb (e.g., "me")

        // Iterate over the dependency edges to find grammatical relations
        for (SemanticGraphEdge edge : dependencies.edgeIterable()) {
            GrammaticalRelation reln = edge.getRelation();
            IndexedWord governor = edge.getGovernor();
            IndexedWord dependent = edge.getDependent();

            // Find the nominal subject (nsubj) of the main verb
            if (reln.getShortName().equals("nsubj") && governor.equals(rootVerb)) {
                subject1 = dependent; // e.g., "She"
            }
            // Find the direct object (dobj) of the main verb
            else if (reln.getShortName().equals("dobj") && governor.equals(rootVerb)) {
                object = dependent; // e.g., "me"
            }
            // Find the complement verb (xcomp or ccomp) of the main verb
            else if ((reln.getShortName().equals("xcomp") || reln.getShortName().equals("ccomp")) && governor.equals(rootVerb)) {
                complementVerb = dependent; // e.g., "feel", "learn"
            }
        }

        if (complementVerb != null) {
            // Find the subject of the complement verb
            for (SemanticGraphEdge edge : dependencies.outgoingEdgeList(complementVerb)) {
                GrammaticalRelation reln = edge.getRelation();
                IndexedWord dependent = edge.getDependent();

                if (reln.getShortName().equals("nsubj") || reln.getShortName().equals("nsubj:xsubj")) {
                    subject2 = dependent; // e.g., "me"
                    break;
                }
            }
        }

        // If the subject of the complement verb is not found, it may be controlled by the object of the main verb
        if (subject2 == null && object != null) {
            subject2 = object;
        }

        // Check if all necessary components are found
        if (subject1 != null && subject2 != null && complementVerb != null) {
            // Swap pronouns to keep consistency
            // Convert the subject pronoun of the complement verb to the new subject pronoun
            String newSubject = OBJECT_TO_SUBJECT_PRONOUN.getOrDefault(subject2.originalText().toLowerCase(), subject2.originalText());
            // Convert the subject pronoun of the main verb to the new object pronoun
            String newObject = SUBJECT_TO_OBJECT_PRONOUN.getOrDefault(subject1.originalText().toLowerCase(), subject1.originalText());

            // Capitalize the new subject for proper sentence formatting
            newSubject = capitalizeFirstLetter(newSubject);

            // Extract the complement phrase starting from the complement verb
            List<IndexedWord> complementWords = dependencies.getSubgraphVertices(complementVerb).stream()
                    // Sort the words according to their positions in the sentence
                    .sorted(Comparator.comparingInt(IndexedWord::index)).collect(Collectors.toList());
            complementWords.add(complementVerb);
            // Remove duplicates and sort the words according to their positions in the sentence
            Set<IndexedWord> uniqueComplementWords = new HashSet<>(complementWords);
            complementWords = new ArrayList<>(uniqueComplementWords);
            complementWords.sort(Comparator.comparingInt(IndexedWord::index));

            // Build the complement phrase by concatenating the words
            StringBuilder complementBuilder = new StringBuilder();
            for (IndexedWord word : complementWords) {
                complementBuilder.append(word.originalText());
                complementBuilder.append(word.after()); // Preserve whitespace
            }
            String complement = complementBuilder.toString().trim();

            // Reconstruct the sentence in the new format
            // "NewSubject complement with NewObject."
            String transformedSentence = String.format("%s %s with %s.",
                    newSubject, complement, newObject);

            return transformedSentence;
        }

        // Return the original sentence if the pattern is not matched
        return sentence;
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
}
