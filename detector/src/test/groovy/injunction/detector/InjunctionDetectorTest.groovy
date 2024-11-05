package injunction.detector

import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.AsyncConditions

/**
 * Test class for {@link InjunctionDetector} using the Spock framework.
 *
 * <p>This class contains a comprehensive suite of test cases to validate the functionality
 * of the {@code InjunctionDetector}. The tests cover various linguistic patterns,
 * pronoun handling, tense transformations, negations, conjunctions, proper names,
 * and edge cases.
 *
 * <p><b>High-Level Overview:</b>
 * <ul>
 *   <li>Ensures that sentences are transformed correctly based on specific patterns.</li>
 *   <li>Checks asynchronous methods for detecting injunctions and profanity.</li>
 *   <li>Validates suggestions for improving sentences with certain conjunctions.</li>
 *   <li>Covers a wide range of grammatical persons, tenses, and sentence structures.</li>
 * </ul>
 *
 * <p><b>Note:</b> The test cases are designed to be both accessible to those new to programming
 * and detailed enough for experts in linguistics to appreciate the nuances of the language processing.
 */
class InjunctionDetectorTest extends Specification {

    InjunctionDetector detector
    TaskExecutor executor

    def setup() {
        executor = new DesktopTaskExecutor()
        detector = new InjunctionDetector(executor)
    }

    /**
     * Tests the {@code transformSentence} method with a variety of sentences
     * to ensure proper transformation according to the defined linguistic patterns.
     *
     * <p>This test covers pronoun swapping, verb tense adjustments, handling of modals,
     * negations, additional clauses, different subjects (pronouns and proper names),
     * and various tenses.
     */
    @Unroll
    def "transformSentence should correctly transform sentences with various subjects and tenses"() {
        given:
        def conditions = new AsyncConditions(1)

        when:
        detector.transformSentence(testSentence, { transformed ->
            conditions.evaluate {
                assert transformed == expectedTransformed
            }
        })

        then:
        conditions.await(300)

        where:
        testSentence                                       | expectedTransformed                                   || comment
        // Simple present tense with pronouns
        "She makes me feel happy."                         | "I feel happy with her."                               || "Simple present tense with pronouns"
        "He makes us feel appreciated."                    | "We feel appreciated with him."                        || "Simple present tense with plural object"
        "They make him feel welcome."                      | "He feels welcome with them."                          || "Simple present tense with plural subject"

        // Simple past tense with pronouns
        "She made me feel happy."                          | "I felt happy with her."                               || "Simple past tense with pronouns"
        "He made us feel appreciated."                     | "We felt appreciated with him."                        || "Simple past tense with plural object"

        // Future tense with modal "will"
        "She will make me feel happy."                     | "I will feel happy with her."                          || "Future tense with modal 'will'"
        "They will make him feel welcome."                 | "He will feel welcome with them."                      || "Future tense with plural subject"

        // Present continuous tense
        "She is making me feel happy."                     | "I am feeling happy with her."                         || "Present continuous tense"
        "They are making him feel welcome."                | "He is feeling welcome with them."                     || "Present continuous with plural subject"

        // Past continuous tense
        "She was making me feel happy."                    | "I was feeling happy with her."                        || "Past continuous tense"
        "They were making him feel welcome."               | "He was feeling welcome with them."                    || "Past continuous with plural subject"

        // Negative sentences
        "She doesn't make me feel sad."                    | "I don't feel sad with her."                           || "Negative sentence with 'doesn't'"
        "They didn't make him feel unwelcome."             | "He didn't feel unwelcome with them."                  || "Negative past tense with 'didn't'"

        // Sentences with proper names
        "Alice makes Bob feel appreciated."                | "Bob feels appreciated with Alice."                    || "Proper names as subject and object"
        "John made Sarah feel special."                    | "Sarah felt special with John."                        || "Past tense with proper names"

        // Sentences with modals
        "She can make me feel happy."                      | "I can feel happy with her."                           || "Modal 'can'"
        "He could make us feel appreciated."               | "We could feel appreciated with him."                  || "Modal 'could'"
        "They might make him feel welcome."                | "He might feel welcome with them."                     || "Modal 'might'"

        // Sentences with "to be" as complement verb
        "She wants me to be confident."                    | "I am confident with her."                             || "Infinitive 'to be' complement"
        "They need him to be ready."                       | "He is ready with them."                               || "Infinitive 'to be' with different subject"

        // Sentences with additional clauses
        "Alice makes Bob feel appreciated when he works hard." | "Bob feels appreciated with Alice when he works hard." || "Additional clause at the end"
        "They make him feel welcome wherever he goes."     | "He feels welcome with them wherever he goes."         || "Relative clause"

        // Sentences with adverbs and adjectives
        "She really makes me feel extremely happy."        | "I feel extremely happy with her."                     || "Adverbs and adjectives"
        "He always makes us feel very appreciated."        | "We feel very appreciated with him."                   || "Adverb 'always' and adjective 'very'"

        // Sentences with idioms
        "She makes me feel on top of the world."           | "I feel on top of the world with her."                 || "Idiomatic expression"

        // Sentences with multiple objects
        "John makes me and my friends feel welcome."       | "We feel welcome with John."                           || "Multiple objects"
        "They make Alice and Bob feel appreciated."        | "They feel appreciated with them."                     || "Plural objects with proper names"

        // Sentences with prepositional phrases
        "He makes me feel at home."                        | "I feel at home with him."                             || "Prepositional phrase"

        // Sentences with conjunctions in complement
        "She makes me feel excited and nervous."           | "I feel excited and nervous with her."                 || "Conjunctions in complement"

        // Sentences with complex verb phrases
        "Alice has been making Bob feel appreciated."      | "Bob has been feeling appreciated with Alice."         || "Present perfect continuous tense"

        // Sentences with reflexive pronouns (should remain unchanged)
        "She makes herself feel happy."                    | "She makes herself feel happy."                        || "Reflexive pronoun, no transformation"

        // Sentences that do not match the pattern (should remain unchanged)
        "This sentence does not match the pattern."        | "This sentence does not match the pattern."            || "No matching pattern"
        "He gives me hope."                                | "He gives me hope."                                    || "Different verb, no transformation"
    }

    /**
     * Tests the {@code suggestInjunctionReplacementAsync} method with a variety of sentences,
     * covering different tenses, subjects, pronouns, and proper names.
     *
     * <p>This test ensures that injunctions are correctly identified and replaced.
     */
    @Unroll
    def "suggestInjunctionReplacementAsync should provide replacements for injunctions with various tenses and subjects"() {
        given:
        def conditions = new AsyncConditions(1)

        when:
        detector.suggestInjunctionReplacementAsync(testSentence, { replacement ->
            conditions.evaluate {
                assert replacement == expectedReplacement
            }
        })

        then:
        conditions.await()

        where:
        testSentence                                     | expectedReplacement                         || comment
        // Present tense with pronouns
        "I don't like this movie."                      | "I do like this movie."                     || "First-person singular present"
        "You can't park here."                          | "You can park here."                        || "Second-person singular present"
        "He doesn't agree with the decision."           | "He does agree with the decision."          || "Third-person singular masculine present"
        "She can't make it tonight."                    | "She can make it tonight."                  || "Third-person singular feminine present"
        "They don't believe the news."                  | "They do believe the news."                 || "Third-person plural present"

        // Past tense with pronouns
        "I didn't enjoy the party."                     | "I did enjoy the party."                    || "First-person singular past"
        "You couldn't find the solution."               | "You could find the solution."              || "Second-person singular past"
        "He wasn't happy about it."                     | "He was happy about it."                    || "Third-person singular masculine past"
        "She hadn't finished her work."                 | "She had finished her work."                || "Third-person singular feminine past"
        "They weren't ready yet."                       | "They were ready yet."                      || "Third-person plural past"

        // Future tense with modals
        "I won't attend the meeting."                   | "I will attend the meeting."                || "First-person singular future"
        "You shouldn't be late."                        | "You should be late."                       || "Second-person singular future"
        "He won't accept defeat."                       | "He will accept defeat."                    || "Third-person singular masculine future"
        "She shouldn't stay up late."                   | "She should stay up late."                  || "Third-person singular feminine future"
        "They won't join us."                           | "They will join us."                        || "Third-person plural future"

        // Sentences with proper names
        "Alice can't find her keys."                    | "Alice can find her keys."                  || "Proper name singular present"
        "Bob didn't finish the project."                | "Bob did finish the project."               || "Proper name singular past"
        "Charlie won't be coming to the party."         | "Charlie will be coming to the party."      || "Proper name singular future"
        "David hasn't called me yet."                   | "David has called me yet."                  || "Proper name singular present perfect"
        "Emma shouldn't eat so much candy."             | "Emma should eat so much candy."            || "Proper name singular modal"

        // Sentences with contractions
        "I can't believe it."                           | "I can believe it."                         || "Contraction with 'can't'"
        "She doesn't know the answer."                  | "She does know the answer."                 || "Contraction with 'doesn't'"
        "He didn't see the sign."                       | "He did see the sign."                      || "Contraction with 'didn't'"
        "They aren't going to the game."                | "They are going to the game."               || "Contraction with 'aren't'"
        "We haven't met before."                        | "We have met before."                       || "Contraction with 'haven't'"

        // Sentences with multiple negations
        "I can't not go."                               | "I can go."                                 || "Double negation"
        "She doesn't dislike him."                      | "She does dislike him."                     || "Negation with 'dislike'"
        "They haven't never been there."                | "They have been there."                     || "Double negation with 'never'"

        // Sentences that should remain unchanged
        "I can believe it."                             | "I can believe it."                         || "No injunction, remains unchanged"
        "She does know the answer."                     | "She does know the answer."                 || "No injunction, remains unchanged"
    }

    /**
     * Tests the {@code suggestConjunctionReplacementAsync} method with a variety of sentences,
     * covering different tenses, subjects, pronouns, proper names, and including both
     * conjunctions and negations.
     *
     * <p>This test ensures that conjunctions are correctly identified and replaced.
     */
    @Unroll
    def "suggestConjunctionReplacementAsync should provide replacements for conjunctions with various tenses and subjects"() {
        given:
        def conditions = new AsyncConditions(1)

        when:
        detector.suggestConjunctionReplacementAsync(testSentence, { replacement ->
            conditions.evaluate {
                assert replacement == expectedReplacement
            }
        })

        then:
        conditions.await()

        where:
        testSentence                                         | expectedReplacement                                || comment
        // Present tense with pronouns
        "I want to help, but I'm too busy."                  | "I want to help, and I'm too busy."                || "First-person singular present"
        "You tried hard, but you failed."                    | "You tried hard, and you failed."                  || "Second-person singular past"
        "He is talented, but lazy."                          | "He is talented, and lazy."                        || "Third-person singular masculine present"
        "She sings beautifully, but rarely."                 | "She sings beautifully, and rarely."               || "Third-person singular feminine present"
        "They acted quickly, but efficiently."               | "They acted quickly, and efficiently."             || "Third-person plural past"

        // Sentences with proper names
        "Alice is smart, but modest."                        | "Alice is smart, and modest."                      || "Proper name singular present"
        "Bob wants to join, but can't."                      | "Bob wants to join, and can't."                    || "Proper name singular present with contraction"
        "Charlie speaks slowly, but clearly."                | "Charlie speaks slowly, and clearly."              || "Proper name singular present"
        "David studied hard, but didn't pass."               | "David studied hard, and didn't pass."             || "Proper name singular past with contraction"
        "Emma is young, but experienced."                    | "Emma is young, and experienced."                  || "Proper name singular present"

        // Sentences with multiple conjunctions
        "I wanted to go, but it was late, but I went anyway."| "I wanted to go, and it was late, and I went anyway." || "Multiple conjunctions"

        // Sentences with negations and conjunctions
        "He didn't win, but he tried his best."              | "He didn't win, and he tried his best."            || "Negation with conjunction"

        // Sentences with different tenses
        "We planned well, but things went wrong."            | "We planned well, and things went wrong."          || "Past tense"
        "They will come, but they might be late."            | "They will come, and they might be late."          || "Future tense with modal"
        "She has arrived, but her luggage hasn't."           | "She has arrived, and her luggage hasn't."         || "Present perfect tense"

        // Sentences that should remain unchanged
        "I like apples and oranges."                         | "I like apples and oranges."                       || "No 'but', remains unchanged"
        "He is not only smart but also kind."                | "He is both smart and kind."                       || "Handled by another method"
    }

    /**
     * Additional test cases for the {@code isInjunction} method, including
     * sentences with various subjects, tenses, pronouns, proper names,
     * and complex negations.
     */
    @Unroll
    def "Detector correctly identifies injunctions in complex sentences"(String testText, boolean expected) {
        expect:
        detector.isInjunction(testText) == expected

        where:
        testText                                                      || expected
        "I can't believe Alice didn't come to the party."             || true
        "Bob doesn't think he can win, but he will try."              || true
        "They shouldn't have left early, but they did."               || true
        "We won't accept defeat."                                     || true
        "Charlie didn't see the sign, so he didn't stop."             || true
        "Emma hasn't finished her work yet."                          || true
        "He could have gone, but he chose not to."                    || true
        "She might not agree with the decision."                      || true
        "They are not going to the concert."                          || true
        "I will not tolerate this behavior."                          || true

        // Sentences without injunctions
        "I can believe Alice came to the party."                      || false
        "Bob thinks he can win, and he will try."                     || false
        "They left early, and they did."                              || false
        "We accept defeat."                                           || false
        "Charlie saw the sign, so he stopped."                        || false
        "Emma finished her work."                                     || false
        "He went because he chose to."                                || false
        "She agrees with the decision."                               || false
        "They are going to the concert."                              || false
        "I tolerate this behavior."                                   || false
    }

    /**
     * Tests that the detector avoids false positives with phrases that contain
     * words similar to negations or conjunctions but are not actually injunctions.
     */
    @Unroll
    def "Detector avoids false positives in sentences with similar words"(String testText) {
        expect:
        !detector.isInjunction(testText)

        where:
        testText << [
                "We cannotionally agreed on the plan.",
                "The butterfly is beautiful.",
                "She is notable in her field.",
                "He buttoned his shirt.",
                "It's a noticeable difference.",
                "They are butlers at the mansion.",
        ]
    }

    /**
     * Additional test cases for the {@code containsProfanity} method,
     * including sentences with and without profane words, using both
     * pronouns and proper names.
     */
    @Unroll
    def "Detector correctly identifies profanity in various sentences"(String testText, boolean expectedProfanity) {
        expect:
        detector.containsProfanity(testText) == expectedProfanity

        where:
        testText                                         || expectedProfanity
        "This is absolutely unacceptable."               || false
        "He used a damn profane word."                   || true
        "That was a hell of a performance."              || true
        "She said a bad word."                           || false
        "I can't believe he said that word."             || false
        "Bob is a damn good player."                     || true
        "This is freaking amazing."                      || true
        "They are such idiots."                          || false
        "Emma is such a badass."                         || true
        "What the heck is going on?"                     || false
    }

    /**
     * Tests that the {@code transformSentence} method leaves sentences
     * unchanged when they do not match the transformation pattern.
     */
    @Unroll
    def "transformSentence leaves non-matching sentences unchanged"(String testSentence) {
        given:
        def conditions = new AsyncConditions(1)

        when:
        detector.transformSentence(testSentence, { transformed ->
            conditions.evaluate {
                assert transformed == testSentence
            }
        })

        then:
        conditions.await()

        where:
        testSentence << [
                "This sentence does not match the pattern.",
                "He gives me hope.",
                "She teaches herself to play piano.",
                "I enjoy reading books.",
                "They are planning a trip.",
                "We will go when it's sunny.",
        ]
    }

    /**
     * Tests the transformation of sentences with complex verb phrases,
     * ensuring that tense and aspect are handled correctly.
     */
    @Unroll
    def "transformSentence handles complex verb phrases correctly"(String testSentence, String expectedTransformed) {
        given:
        def conditions = new AsyncConditions(1)

        when:
        detector.transformSentence(testSentence, { transformed ->
            conditions.evaluate {
                assert transformed == expectedTransformed
            }
        })

        then:
        conditions.await()

        where:
        testSentence                                       | expectedTransformed
        "She has been making me feel happy."               | "I have been feeling happy with her."
        "They will have made him feel welcome."            | "He will have felt welcome with them."
        "He had been making us feel appreciated."          | "We had been feeling appreciated with him."
        "Alice is going to make Bob feel special."         | "Bob is going to feel special with Alice."
    }

    /**
     * Tests the transformation of sentences with modal verbs and negations.
     */
    @Unroll
    def "transformSentence handles modals and negations correctly"(String testSentence, String expectedTransformed) {
        given:
        def conditions = new AsyncConditions(1)

        when:
        detector.transformSentence(testSentence, { transformed ->
            conditions.evaluate {
                assert transformed == expectedTransformed
            }
        })

        then:
        conditions.await()

        where:
        testSentence                                       | expectedTransformed
        "She can't make me feel sad."                      | "I can't feel sad with her."
        "He shouldn't make us feel unwelcome."             | "We shouldn't feel unwelcome with him."
        "They might not make him feel included."           | "He might not feel included with them."
        "Alice wouldn't make Bob feel ignored."            | "Bob wouldn't feel ignored with Alice."
    }
}
