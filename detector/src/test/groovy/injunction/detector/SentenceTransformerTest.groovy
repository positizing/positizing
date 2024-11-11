package injunction.detector

import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.AsyncConditions

/**
 * Test class for {@link NegativeSpeechDetector} using the Spock framework.
 *
 * <p>This class contains a comprehensive suite of test cases to validate the functionality
 * of the {@code InjunctionDetector}. The tests cover various linguistic patterns,
 * pronoun handling, tense transformations, negations, conjunctions, and edge cases.
 *
 * <p><b>High-Level Overview:</b>
 * <ul>
 *   <li>Ensures that sentences are transformed correctly based on specific patterns.</li>
 *   <li>Checks asynchronous methods for detecting injunctions and profanity.</li>
 *   <li>Validates suggestions for improving sentences with certain conjunctions.</li>
 * </ul>
 *
 * <p><b>Note:</b> The test cases are designed to be both accessible to those new to programming
 * and detailed enough for experts in linguistics to appreciate the nuances of the language processing.
 */
class SentenceTransformerTest extends Specification {

    int testTimeout = 1
    // uncomment this when debugging
//    int testTimeout = 300
    NegativeSpeechDetector detector
    TaskExecutor executor

    def setup() {
        executor = new DesktopTaskExecutor()
        detector = new NegativeSpeechDetector(executor)
    }

    /**
     * Tests the {@code transformSentence} method with a variety of sentences
     * to ensure proper transformation according to the defined linguistic patterns.
     *
     * <p>This test covers pronoun swapping, verb tense adjustments, handling of modals,
     * negations, additional clauses, and different sentence structures.
     */
    def "transformSentence should correctly transform various sentences"() {
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
        testSentence                                      | expectedTransformed
        // Test Case 1: Simple transformation with pronoun swapping
        "She makes me feel happy."                        | "I feel happy with her."
        // Test Case 2: Different pronouns and plural object
        "He makes us feel appreciated."                   | "We feel appreciated with him."
        // Test Case 3: Plural subjects and objects remain unchanged
        "They make them feel welcome."                    | "They feel welcome with them."
        // Test Case 4: Different main verb
        "You help me learn new skills."                   | "I learn new skills with you."
        // Test Case 5: Complement verb in infinitive form
        "She encourages him to be confident."             | "He is confident with her."
        // Test Case 6: Modal verb in the main verb phrase
        "They want me to succeed."                        | "I succeed with them."
        // Test Case 7: Sentence without matching pattern remains unchanged
        "This sentence does not match the pattern."       | "This sentence does not match the pattern."
        // Test Case 8: Sentence with reflexive pronoun remains unchanged
        "She teaches herself to play piano."              | "She teaches herself to play piano."
        // Test Case 9: Sentence with indirect object remains unchanged
        "He gives me hope."                               | "He gives me hope."
        // Test Case 10: Passive voice sentence remains unchanged
        "I am made to feel special by her."               | "I am made to feel special by her."
        // Test Case 11: Past tense main verb
        "She made me feel happy."                         | "I felt happy with her."
        // Test Case 12: Future tense with modal "will"
        "He will make me feel proud."                     | "I will feel proud with him."
        // Test Case 13: Negative sentence with negation
        "She doesn't make me feel sad."                   | "I don't feel sad with her."
        // Test Case 14: Sentence with additional clause
        "They make me feel inspired when they perform."   | "I feel inspired with them when they perform."
        // Test Case 15: Sentence with adverb
        "He really makes me feel loved."                  | "I feel loved with him."
        // Test Case 16: Sentence with adjective intensifier
        "She makes me feel extremely happy."              | "I feel extremely happy with her."
        // Test Case 17: Sentence with multiple objects
        "He makes me and my friends feel welcome."        | "We feel welcome with him."
        // Test Case 18: Sentence with prepositional phrase
        "She makes me feel at home."                      | "I feel at home with her."
        // Test Case 19: Sentence with conjunction in complement
        "They make us feel excited and nervous."          | "We feel excited and nervous with them."
        // Test Case 20: Sentence with idiomatic expression
        "He makes me feel on top of the world."           | "I feel on top of the world with him."
    }

    /**
     * Tests the {@code transformSentence} method with sentences that include
     * various tenses and additional clauses to ensure proper handling.
     *
     * <p>This test specifically focuses on tense transformations and inclusion
     * of subordinate clauses in the transformed sentence.
     */
    def "transformSentence should correctly handle tense and clause transformations"() {
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
        testSentence                                      | expectedTransformed
        // Test Case 1: Complement verb in infinitive form with "to be"
        "She encourages him to be confident."             | "He is confident with her."
        // Test Case 2: Modal verb "want" with infinitive complement
        "They want me to succeed."                        | "I succeed with them."
        // Test Case 3: Past tense main verb
        "She made me feel happy."                         | "I felt happy with her."
        // Test Case 4: Future tense with modal "will"
        "He will make me feel proud."                     | "I will feel proud with him."
        // Test Case 5: Negative sentence preserving negation
        "She doesn't make me feel sad."                   | "I don't feel sad with her."
        // Test Case 6: Inclusion of subordinate clause
        "They make me feel inspired when they perform."   | "I feel inspired with them when they perform."
    }

    /**
     * Tests the correct handling of different pronouns in the transformation.
     */
    def "transformSentence should handle different pronouns correctly"() {
        given:
        String testSentence = "He makes us feel welcome."
        String expectedTransformed = "We feel welcome with him."
        def conditions = new AsyncConditions(1)

        when:
        detector.transformSentence(testSentence, { transformed ->
            conditions.evaluate {
                assert transformed == expectedTransformed
            }
        })

        then:
        conditions.await(testTimeout)
    }

    /**
     * Tests that sentences not matching the specific pattern remain unchanged.
     */
    def "transformSentence should leave sentences unchanged when pattern does not match"() {
        given:
        String testSentence = "This sentence does not match the pattern."
        def conditions = new AsyncConditions(1)

        when:
        detector.transformSentence(testSentence, { transformed ->
            conditions.evaluate {
                assert transformed == testSentence
            }
        })

        then:
        conditions.await(testTimeout)
    }

    /**
     * Tests the asynchronous detection of injunctions.
     */
    def "isInjunctionAsync should detect injunctions asynchronously"() {
        given:
        String testSentence = "I can't do this."
        def conditions = new AsyncConditions(1)

        when:
        detector.isInjunctionAsync(testSentence, { result ->
            conditions.evaluate {
                assert result == true
            }
        })

        then:
        conditions.await(testTimeout)
    }

    /**
     * Tests the asynchronous detection of profanity.
     */
    def "containsProfanityAsync should detect profanity asynchronously"() {
        given:
        String testSentence = "That was a damn good performance."
        def conditions = new AsyncConditions(1)

        when:
        detector.containsProfanityAsync(testSentence, { result ->
            conditions.evaluate {
                assert result == true
            }
        })

        then:
        conditions.await(testTimeout)
    }

    /**
     * Tests the asynchronous suggestion of improved sentences when patterns are detected.
     */
    def "suggestImprovedSentenceAsync should provide improved sentences asynchronously"() {
        given:
        String testSentence = "She is not only talented but also hardworking."
        String expectedImproved = "She is both talented and hardworking."
        def conditions = new AsyncConditions(1)

        when:
        detector.suggestImprovedSentenceAsync(testSentence, { improved ->
            conditions.evaluate {
                assert improved == expectedImproved
            }
        })

        then:
        conditions.await(testTimeout)
    }

    /**
     * Tests the transformation of multiple sentences in succession.
     */
    def "transformSentence should correctly transform multiple sentences"() {
        given:
        List<String> testSentences = [
                "She makes me feel happy.",
                "He makes us feel welcome.",
                "They make him feel important."
        ]
        List<String> expectedTransformed = [
                "I feel happy with her.",
                "We feel welcome with him.",
                "He feels important with them."
        ]
        def conditions = new AsyncConditions(testSentences.size())

        when:
        testSentences.eachWithIndex { sentence, index ->
            detector.transformSentence(sentence, { transformed ->
                conditions.evaluate {
                    assert transformed == expectedTransformed[index]
                }
            })
        }

        then:
        conditions.await(testTimeout)
    }

    /**
     * Tests the detection of injunctions asynchronously for multiple sentences.
     */
    def "isInjunctionAsync should correctly detect injunctions in multiple sentences"() {
        given:
        List<String> testSentences = [
                "I can't believe this happened!",
                "This is a clean sentence.",
                "Don't let them stop you."
        ]
        List<Boolean> expectedResults = [true, false, true]
        def conditions = new AsyncConditions(testSentences.size())

        when:
        testSentences.eachWithIndex { sentence, index ->
            detector.isInjunctionAsync(sentence, { result ->
                conditions.evaluate {
                    assert result == expectedResults[index]
                }
            })
        }

        then:
        conditions.await(testTimeout)
    }

    /**
     * Tests the detection of profanity asynchronously for multiple sentences.
     */
    def "containsProfanityAsync should correctly detect profanity in multiple sentences"() {
        given:
        List<String> testSentences = [
                "This is absolutely unacceptable.",
                "He used a profane word.",
                "That was a damn good performance."
        ]
        List<Boolean> expectedResults = [false, false, true]
        def conditions = new AsyncConditions(testSentences.size())

        when:
        testSentences.eachWithIndex { sentence, index ->
            detector.containsProfanityAsync(sentence, { result ->
                conditions.evaluate {
                    assert result == expectedResults[index]
                }
            })
        }

        then:
        conditions.await(testTimeout)
    }

    /**
     * Tests the suggestion of injunction replacements for sentences with different pronouns.
     *
     * <p>This test ensures that the {@code suggestInjunctionReplacementAsync} method provides
     * accurate replacements across various grammatical persons.
     */
    @Unroll
    def "suggestInjunctionReplacementAsync should provide replacements for injunctions with different pronouns"() {
        given:
        def conditions = new AsyncConditions(1)

        when:
        detector.suggestInjunctionReplacementAsync(testSentence, { replacement ->
            conditions.evaluate {
                assert replacement == expectedReplacement
            }
        })

        then:
        conditions.await(testTimeout)

        where:
        testSentence                             | expectedReplacement
        // First-person singular (I)
        "I can't believe this happened!"         | "I can believe this happened."
        "I don't know the answer."               | "I do know the answer."
        "I shouldn't have eaten that."           | "I should have eaten that."
        "I won't give up."                       | "I will give up."
        "I haven't finished my work."            | "I have finished my work."
        "I couldn't find my keys."               | "I could find my keys."
        // Second-person singular/plural (You)
        "You shouldn't worry about it."          | "You should worry about it."
        "You don't need to come."                | "You do need to come."
        "You can't park here."                   | "You can park here."
        "You haven't met him yet."               | "You have met him yet."
        "You didn't call me back."               | "You did call me back."
        "You won't regret this."                 | "You will regret this."
        // Third-person singular masculine (He)
        "He doesn't like the new rules."         | "He does like the new rules."
        "He can't swim."                         | "He can swim."
        "He didn't attend the class."            | "He did attend the class."
        "He shouldn't be late."                  | "He should be late."
        "He hasn't arrived yet."                 | "He has arrived yet."
        "He won't accept defeat."                | "He will accept defeat."
        // Third-person singular feminine (She)
        "She won't attend the meeting."          | "She will attend the meeting."
        "She doesn't agree with the decision."   | "She does agree with the decision."
        "She can't make it tonight."             | "She can make it tonight."
        "She didn't finish the project."         | "She did finish the project."
        "She hasn't called me."                  | "She has called me."
        "She shouldn't stay up late."            | "She should stay up late."
        // Third-person plural (They)
        "They couldn't find the solution."       | "They could find the solution."
        "They don't believe the news."           | "They do believe the news."
        "They haven't responded yet."            | "They have responded yet."
        "They shouldn't complain."               | "They should complain."
        "They didn't follow the instructions."   | "They did follow the instructions."
        "They won't join us."                    | "They will join us."
        // First-person plural (We)
        "We aren't going to the party."          | "We are going to the party."
        "We can't solve this problem."           | "We can solve this problem."
        "We don't agree with the terms."         | "We do agree with the terms."
        "We didn't win the game."                | "We did win the game."
        "We haven't seen that movie."            | "We have seen that movie."
        "We shouldn't waste time."               | "We should waste time."
        // Third-person singular neutral (It)
        "It isn't working as expected."          | "It is working as expected."
        "It doesn't make sense."                 | "It does make sense."
        "It can't be true."                      | "It can be true."
        "It hasn't started yet."                 | "It has started yet."
        "It shouldn't take long."                | "It should take long."
        "It didn't rain yesterday."              | "It did rain yesterday."
    }

    /**
     * Tests the suggestion of conjunction replacements for sentences with different pronouns.
     *
     * <p>This test ensures that the {@code suggestConjunctionReplacementAsync} method replaces
     * "but" with "and" appropriately across various grammatical persons.
     */
    @Unroll
    def "suggestConjunctionReplacementAsync should provide replacements for conjunctions with different pronouns"() {
        given:
        def conditions = new AsyncConditions(1)

        when:
        detector.suggestConjunctionReplacementAsync(testSentence, { replacement ->
            conditions.evaluate {
                assert replacement == expectedReplacement
            }
        })

        then:
        conditions.await(testTimeout)

        where:
        testSentence                                  | expectedReplacement
        // First-person singular (I)
        "I want to help, but I'm too busy."           | "I want to help, and I'm too busy."
        "I tried hard, but I failed."                 | "I tried hard, and I failed."
        "I like coffee, but not tea."                 | "I like coffee, and not tea."
        "I need to rest, but I have work to do."      | "I need to rest, and I have work to do."
        "I agree with you, but others don't."         | "I agree with you, and others don't."
        "I planned well, but things went wrong."      | "I planned well, and things went wrong."
        // Second-person singular/plural (You)
        "You tried hard, but you failed."             | "You tried hard, and you failed."
        "You want to go, but you can't."              | "You want to go, and you can't."
        "You like ice cream, but not cake."           | "You like ice cream, and not cake."
        "You studied, but didn't pass."               | "You studied, and didn't pass."
        "You can stay, but it's up to you."           | "You can stay, and it's up to you."
        "You called me, but I missed it."             | "You called me, and I missed it."
        // Third-person singular masculine (He)
        "He is talented, but lazy."                   | "He is talented, and lazy."
        "He wants to join, but can't."                | "He wants to join, and can't."
        "He speaks slowly, but clearly."              | "He speaks slowly, and clearly."
        "He studied a lot, but didn't pass."          | "He studied a lot, and didn't pass."
        "He is young, but experienced."               | "He is young, and experienced."
        "He agreed, but with conditions."             | "He agreed, and with conditions."
        // Third-person singular feminine (She)
        "She studied a lot, but didn't pass."         | "She studied a lot, and didn't pass."
        "She is smart, but modest."                   | "She is smart, and modest."
        "She wants to help, but can't."               | "She wants to help, and can't."
        "She sings beautifully, but rarely."          | "She sings beautifully, and rarely."
        "She tried, but failed."                      | "She tried, and failed."
        "She is friendly, but shy."                   | "She is friendly, and shy."
        // Third-person plural (They)
        "They were ready, but the event was canceled."| "They were ready, and the event was canceled."
        "They want to join, but need permission."     | "They want to join, and need permission."
        "They acted quickly, but efficiently."        | "They acted quickly, and efficiently."
        "They agreed, but not entirely."              | "They agreed, and not entirely."
        "They came early, but left late."             | "They came early, and left late."
        "They won the game, but lost the series."     | "They won the game, and lost the series."
        // First-person plural (We)
        "We planned well, but things went wrong."     | "We planned well, and things went wrong."
        "We want to help, but lack resources."        | "We want to help, and lack resources."
        "We tried hard, but didn't succeed."          | "We tried hard, and didn't succeed."
        "We are excited, but nervous."                | "We are excited, and nervous."
        "We agreed, but with reservations."           | "We agreed, and with reservations."
        "We arrived early, but the event was delayed."| "We arrived early, and the event was delayed."
        // Third-person singular neutral (It)
        "It looks good, but feels cheap."             | "It looks good, and feels cheap."
        "It works, but not perfectly."                | "It works, and not perfectly."
        "It started late, but finished on time."      | "It started late, and finished on time."
        "It seems simple, but is complicated."        | "It seems simple, and is complicated."
        "It was fun, but tiring."                     | "It was fun, and tiring."
        "It rains, but not heavily."                  | "It rains, and not heavily."
    }

    /**
     * Tests that the detector can identify simple phrases containing "not".
     */
    def "Detector can find simple 'not' phrases"() {
        expect:
        detector.isInjunction("You don't have time for distractions")
    }

    /**
     * Tests the detection of injunctions across a wide variety of phrases.
     *
     * <p>This test ensures that various negations and modal verbs are correctly identified.
     */
    @Unroll
    def "Detector works on a wide variety of phrases containing injunctions"(String testText) {
        expect:
        detector.isInjunction(testText)

        where:
        testText << [
                "I will never read this",
                "I do not like reading this",
                "I'm not contradicting you",
                "It's not the best for you",
                "This isn't working",
                "You don't have time for distractions",
                "I can't do this anymore",
                "Don't let me down",
                "I shouldn't have done that",
                "He won't be coming",
        ]
    }

    /**
     * Tests that the detector identifies phrases containing "should" as injunctions.
     */
    @Unroll
    def "Detector can find phrases containing 'should'"(String testText) {
        expect:
        detector.isInjunction(testText)

        where:
        testText << [
                "I should really read this",
                "I shouldn't do this",
                "You should listen to me",
                "They should not have left early",
                "We should consider all options",
        ]
    }

    /**
     * Tests that the detector identifies phrases containing "but" as injunctions.
     */
    @Unroll
    def "Detector can find phrases containing 'but'"(String testText) {
        expect:
        detector.isInjunction(testText)

        where:
        testText << [
                "I wanted to go, but it was too late.",
                "She tried her best, but failed.",
                "He is smart, but lazy.",
                "We could go, but I don't want to.",
                "It's a good idea, but it's not practical.",
        ]
    }

    /**
     * Tests that the detector does not falsely identify neutral phrases as injunctions.
     */
    @Unroll
    def "Detector does not detect injunctions in neutral phrases"(String testText) {
        expect:
        !detector.isInjunction(testText)

        where:
        testText << [
                "This is a clean sentence.",
                "I went to the store yesterday.",
                "She enjoys reading books.",
                "He plays soccer on weekends.",
                "They are planning a trip.",
        ]
    }

    /**
     * Tests that the detector correctly identifies phrases without "but" as non-injunctions.
     */
    @Unroll
    def "Detector correctly identifies phrases without 'but' as non-injunctions"(String testText) {
        expect:
        !detector.isInjunction(testText)

        where:
        testText << [
                "I like apples and oranges.",
                "She sings and dances.",
                "We will go when it's sunny.",
                "He wants to learn more.",
                "They decided to stay.",
        ]
    }

    /**
     * Tests that the detector handles phrases with "but" that are not injunctions.
     */
    @Unroll
    def "Detector handles phrases with 'but' that are not injunctions"(String testText) {
        expect:
        !detector.isInjunction(testText)

        where:
        testText << [
                "I like apples but also enjoy oranges.",
                "He is not only smart but also kind.",
                "She is young but very experienced.",
                "They are rich but humble.",
                "It's challenging but rewarding.",
        ]
    }

    /**
     * Tests the detection of injunctions in complex phrases with expected results.
     */
    @Unroll
    def "Detector correctly identifies complex phrases with expected results"(String testText, boolean expected) {
        expect:
        detector.isInjunction(testText) == expected

        where:
        testText                                             || expected
        "I wanted to go, but it was too late."               || true
        "She tried her best, but failed."                    || true
        "This is a clean sentence."                          || false
        "I can't do this."                                   || true
        "We will not allow that to happen."                  || true
        "Nothing can stop us now."                           || false
        "I thought about it but decided against it."         || true
        "They will be here soon."                            || false
        "He didn't know what to say."                        || true
        "You must not forget your keys."                     || true
        "Let's go to the park and have fun."                 || false
        "She said she'd come, but I doubt it."               || true
        "We should go now."                                  || true
        "It's sunny outside."                                || false
    }

    /**
     * Tests that the detector handles phrases with both injunctions and profanity.
     */
    @Unroll
    def "Detector handles phrases with both injunctions and profanity"(String testText, boolean expectedInjunction, boolean expectedProfanity) {
        expect:
        detector.isInjunction(testText) == expectedInjunction
        detector.containsProfanity(testText) == expectedProfanity

        where:
        testText                                || expectedInjunction || expectedProfanity
        "I can't believe this happened!"        || true               || false
        "He is a brilliant individual."         || false              || false
        "This is an unacceptable situation."    || false              || false
        "Don't let them stop you."              || true               || false
        "She won't be able to make it."         || true               || false
        "We are going to succeed."              || false              || false
        "This is absolutely bitchin"              || false              || true
    }

    /**
     * Tests that the detector does not detect injunctions in positive phrases with "but".
     */
    @Unroll
    def "Detector does not detect injunctions in phrases with 'but' used positively"(String testText) {
        expect:
        !detector.isInjunction(testText)

        where:
        testText << [
                "She is not only talented but also hardworking.",
                "The cake was sweet but not too sweet.",
                "It's difficult but achievable.",
                "He is young but very wise.",
                "They moved quickly but silently.",
        ]
    }

    /**
     * Tests the detection of injunctions in edge cases involving "not" and "but".
     */
    @Unroll
    def "Detector handles edge cases for 'not' and 'but'"(String testText, boolean expected) {
        expect:
        detector.isInjunction(testText) == expected

        where:
        testText                                        || expected
        "Not everything is lost."                       || true
        "But for the grace of God."                     || true
        "He is neither rich nor famous."                || false
        "I can't not go."                               || true
        "But wait, there's more!"                       || true
    }

    /**
     * Tests the detection of injunctions in phrases with multiple negations and conjunctions.
     */
    @Unroll
    def "Detector correctly identifies phrases with negations and conjunctions"(String testText, boolean expected) {
        expect:
        detector.isInjunction(testText) == expected

        where:
        testText                                                   || expected
        "I would go, but I can't."                                 || true
        "They should not have done that."                          || true
        "He could, but he won't."                                  || true
        "It's not only raining but also cold."                     || true
        "She wants to help, but doesn't know how."                 || true
        "We can try, but success is not guaranteed."               || true
        "I appreciate it, but no thank you."                       || true
        "I neither agree nor disagree."                            || false
        "He is not happy about it, but he accepts it."             || true
        "They don't like it, but they need it."                    || true
    }

    /**
     * Tests the detection and improvement of sentences with the "not only...but also..." pattern.
     */
    def "Detector can find and improve sentences with 'not only...but also...'"() {
        given:
        String testText = "She is not only talented but also hardworking."

        when:
        boolean hasInjunction = detector.isInjunction(testText)
        String improvedText = detector.suggestImprovedSentence(testText)

        then:
        hasInjunction == true
        improvedText == "She is both talented and hardworking."
    }

    /**
     * Tests the detection and improvement of multiple sentences with the "not only...but also..." pattern.
     */
    @Unroll
    def "Detector can find and improve multiple sentences"(String testText, String expectedImprovedText) {
        when:
        boolean hasInjunction = detector.isInjunction(testText)
        String improvedText = detector.suggestImprovedSentence(testText)

        then:
        hasInjunction == true
        improvedText == expectedImprovedText

        where:
        testText                                             || expectedImprovedText
        "She is not only talented but also hardworking."     || "She is both talented and hardworking."
        "He is not only smart but also kind."                || "He is both smart and kind."
        "They were not only exhausted but also hungry."      || "They were both exhausted and hungry."
        "The project is not only ambitious but also feasible." || "The project is both ambitious and feasible."
    }

    /**
     * Tests that the detector leaves sentences unchanged when no improvement is needed.
     */
    @Unroll
    def "Detector leaves sentences unchanged when no improvement is needed"(String testText) {
        when:
        boolean hasInjunction = detector.isInjunction(testText)
        String improvedText = detector.suggestImprovedSentence(testText)

        then:
        hasInjunction == false
        improvedText == testText

        where:
        testText << [
                "She is both talented and hardworking.",
                "He is smart and kind.",
                "They were exhausted and hungry.",
                "The project is ambitious and feasible.",
                "This is a regular sentence without the pattern."
        ]
    }

    /**
     * Tests that the detector identifies sentences with multiple injunctions.
     */
    @Unroll
    def "Detector identifies sentences with multiple injunctions"(String testText) {
        expect:
        detector.isInjunction(testText)

        where:
        testText << [
                "I can't do this, but I should try.",
                "You should not go there, but if you must, be careful.",
                "They won't come because they don't like crowds.",
        ]
    }

    /**
     * Tests that the detector avoids false positives with substring matches.
     */
    @Unroll
    def "Detector avoids false positives with substring matches"(String testText) {
        expect:
        !detector.isInjunction(testText)

        where:
        testText << [
                "We can conditionally agree on this.",
                "The notorious bandit struck again.",
                "She is notable in her field.",
                "He buttered the toast.",
                "The button is stuck.",
        ]
    }

    /**
     * Tests the detection of profanity in various phrases.
     */
    @Unroll
    def "Detector correctly identifies profanity in phrases"(String testText, boolean expectedProfanity) {
        expect:
        detector.containsProfanity(testText) == expectedProfanity

        where:
        testText                                  || expectedProfanity
        "This is absolutely unacceptable."        || false
        "He used a profane word."                 || false
        "That was a damn good performance."       || true
        "She said a bad word."                    || false
        // Additional phrases with actual profane words from the list
    }

    /**
     * Tests the transformation of a specific sentence as per the example.
     */
    def "Detector transforms 'She makes me feel happy' into 'I feel happy with her'"() {
        given:
        String testText = "She makes me feel happy."
        String expectedTransformedText = "I feel happy with her."
        def conditions = new AsyncConditions(1)

        when:
        detector.transformSentence(testText, { transformed ->
            conditions.evaluate {
                assert transformed == expectedTransformedText
            }
        })

        then:
        conditions.await(testTimeout)
    }
}