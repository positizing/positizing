package injunction.detector


import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

/**
 * Test class for InjunctionDetector using Spock framework.
 */
class SentenceTransformerTest extends Specification {

    InjunctionDetector detector
    TaskExecutor executor

    def setup() {
        executor = new DesktopTaskExecutor()
        detector = new InjunctionDetector(executor)
    }

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
        // Test Case 1: Original test case
        "She makes me feel happy."                        | "I feel happy with her."
        // Test Case 2: Different pronouns
        "He makes us feel appreciated."                   | "We feel appreciated with him."
        // Test Case 3: Plural subjects and objects
        "They make them feel welcome."                    | "They feel welcome with them."
        // Test Case 4: Different verbs
        "You help me learn new skills."                   | "I learn new skills with you."
        // Test Case 5: Complex complement
        "She encourages him to be confident."             | "He is confident with her."
        // Test Case 6: Modal verb in complement
        "They want me to succeed."                        | "I succeed with them."
        // Test Case 7: Sentence without matching pattern
        "This sentence does not match the pattern."       | "This sentence does not match the pattern."
        // Test Case 8: Reflexive pronoun
        "She teaches herself to play piano."              | "She teaches herself to play piano."
        // Test Case 9: Indirect object
        "He gives me hope."                               | "He gives me hope."
        // Test Case 10: Passive voice
        "I am made to feel special by her."               | "I am made to feel special by her."
        // Test Case 11: Different tense
        "She made me feel happy."                         | "I felt happy with her."
        // Test Case 12: Future tense
        "He will make me feel proud."                     | "I will feel proud with him."
        // Test Case 13: Negative sentence
        "She doesn't make me feel sad."                   | "I don't feel sad with her."
        // Test Case 14: Sentence with additional clauses
        "They make me feel inspired when they perform."   | "I feel inspired with them when they perform."
        // Test Case 15: Sentence with adverbs
        "He really makes me feel loved."                  | "I feel loved with him."
        // Test Case 16: Sentence with adjectives
        "She makes me feel extremely happy."              | "I feel extremely happy with her."
        // Test Case 17: Sentence with multiple objects
        "He makes me and my friends feel welcome."        | "We feel welcome with him."
        // Test Case 18: Sentence with prepositional phrase
        "She makes me feel at home."                      | "I feel at home with her."
        // Test Case 19: Sentence with conjunction
        "They make us feel excited and nervous."          | "We feel excited and nervous with them."
        // Test Case 20: Sentence with idiom
        "He makes me feel on top of the world."           | "I feel on top of the world with him."
    }

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
        conditions.await()
    }

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
        conditions.await()
    }

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
        conditions.await()
    }

    def "containsProfanityAsync should detect profanity asynchronously"() {
        given:
        String testSentence = "This is absolutely unacceptable."
        def conditions = new AsyncConditions(1)

        when:
        detector.containsProfanityAsync(testSentence, { result ->
            conditions.evaluate {
                assert result == false
            }
        })

        then:
        conditions.await()
    }

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
        conditions.await()
    }

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
        conditions.await()
    }

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
        conditions.await()
    }

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
        conditions.await()
    }

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
        conditions.await()
    }

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
        conditions.await()

        where:
        // First-person singular (I)
        testSentence                             | expectedReplacement              || comment
        "I can't believe this happened!"         | "I can believe this happened."   || "First-person singular (I)"
        "I don't know the answer."               | "I do know the answer."          || "First-person singular (I)"
        "I shouldn't have eaten that."           | "I should have eaten that."      || "First-person singular (I)"
        "I won't give up."                       | "I will give up."                || "First-person singular (I)"
        "I haven't finished my work."            | "I have finished my work."       || "First-person singular (I)"
        "I couldn't find my keys."               | "I could find my keys."          || "First-person singular (I)"

        // Second-person singular/plural (You)
        "You shouldn't worry about it."          | "You should worry about it."     || "Second-person singular/plural (You)"
        "You don't need to come."                | "You do need to come."           || "Second-person singular/plural (You)"
        "You can't park here."                   | "You can park here."             || "Second-person singular/plural (You)"
        "You haven't met him yet."               | "You have met him yet."          || "Second-person singular/plural (You)"
        "You didn't call me back."               | "You did call me back."          || "Second-person singular/plural (You)"
        "You won't regret this."                 | "You will regret this."          || "Second-person singular/plural (You)"

        // Third-person singular masculine (He)
        "He doesn't like the new rules."         | "He does like the new rules."    || "Third-person singular masculine (He)"
        "He can't swim."                         | "He can swim."                   || "Third-person singular masculine (He)"
        "He didn't attend the class."            | "He did attend the class."       || "Third-person singular masculine (He)"
        "He shouldn't be late."                  | "He should be late."             || "Third-person singular masculine (He)"
        "He hasn't arrived yet."                 | "He has arrived yet."            || "Third-person singular masculine (He)"
        "He won't accept defeat."                | "He will accept defeat."         || "Third-person singular masculine (He)"

        // Third-person singular feminine (She)
        "She won't attend the meeting."          | "She will attend the meeting."   || "Third-person singular feminine (She)"
        "She doesn't agree with the decision."   | "She does agree with the decision." || "Third-person singular feminine (She)"
        "She can't make it tonight."             | "She can make it tonight."       || "Third-person singular feminine (She)"
        "She didn't finish the project."         | "She did finish the project."    || "Third-person singular feminine (She)"
        "She hasn't called me."                  | "She has called me."             || "Third-person singular feminine (She)"
        "She shouldn't stay up late."            | "She should stay up late."       || "Third-person singular feminine (She)"

        // Third-person plural (They)
        "They couldn't find the solution."       | "They could find the solution."  || "Third-person plural (They)"
        "They don't believe the news."           | "They do believe the news."      || "Third-person plural (They)"
        "They haven't responded yet."            | "They have responded yet."       || "Third-person plural (They)"
        "They shouldn't complain."               | "They should complain."          || "Third-person plural (They)"
        "They didn't follow the instructions."   | "They did follow the instructions." || "Third-person plural (They)"
        "They won't join us."                    | "They will join us."             || "Third-person plural (They)"

        // First-person plural (We)
        "We aren't going to the party."          | "We are going to the party."     || "First-person plural (We)"
        "We can't solve this problem."           | "We can solve this problem."     || "First-person plural (We)"
        "We don't agree with the terms."         | "We do agree with the terms."    || "First-person plural (We)"
        "We didn't win the game."                | "We did win the game."           || "First-person plural (We)"
        "We haven't seen that movie."            | "We have seen that movie."       || "First-person plural (We)"
        "We shouldn't waste time."               | "We should waste time."          || "First-person plural (We)"

        // Third-person singular neutral (It)
        "It isn't working as expected."          | "It is working as expected."     || "Third-person singular neutral (It)"
        "It doesn't make sense."                 | "It does make sense."            || "Third-person singular neutral (It)"
        "It can't be true."                      | "It can be true."                || "Third-person singular neutral (It)"
        "It hasn't started yet."                 | "It has started yet."            || "Third-person singular neutral (It)"
        "It shouldn't take long."                | "It should take long."           || "Third-person singular neutral (It)"
        "It didn't rain yesterday."              | "It did rain yesterday."         || "Third-person singular neutral (It)"
    }

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
        conditions.await()

        where:
        // First-person singular (I)
        testSentence                                  | expectedReplacement                        || comment
        "I want to help, but I'm too busy."           | "I want to help, and I'm too busy."        || "First-person singular (I)"
        "I tried hard, but I failed."                 | "I tried hard, and I failed."              || "First-person singular (I)"
        "I like coffee, but not tea."                 | "I like coffee, and not tea."              || "First-person singular (I)"
        "I need to rest, but I have work to do."      | "I need to rest, and I have work to do."   || "First-person singular (I)"
        "I agree with you, but others don't."         | "I agree with you, and others don't."      || "First-person singular (I)"
        "I planned well, but things went wrong."      | "I planned well, and things went wrong."   || "First-person singular (I)"

        // Second-person singular/plural (You)
        "You tried hard, but you failed."             | "You tried hard, and you failed."          || "Second-person singular/plural (You)"
        "You want to go, but you can't."              | "You want to go, and you can't."           || "Second-person singular/plural (You)"
        "You like ice cream, but not cake."           | "You like ice cream, and not cake."        || "Second-person singular/plural (You)"
        "You studied, but didn't pass."               | "You studied, and didn't pass."            || "Second-person singular/plural (You)"
        "You can stay, but it's up to you."           | "You can stay, and it's up to you."        || "Second-person singular/plural (You)"
        "You called me, but I missed it."             | "You called me, and I missed it."          || "Second-person singular/plural (You)"

        // Third-person singular masculine (He)
        "He is talented, but lazy."                   | "He is talented, and lazy."                || "Third-person singular masculine (He)"
        "He wants to join, but can't."                | "He wants to join, and can't."             || "Third-person singular masculine (He)"
        "He speaks slowly, but clearly."              | "He speaks slowly, and clearly."           || "Third-person singular masculine (He)"
        "He studied a lot, but didn't pass."          | "He studied a lot, and didn't pass."       || "Third-person singular masculine (He)"
        "He is young, but experienced."               | "He is young, and experienced."            || "Third-person singular masculine (He)"
        "He agreed, but with conditions."             | "He agreed, and with conditions."          || "Third-person singular masculine (He)"

        // Third-person singular feminine (She)
        "She studied a lot, but didn't pass."         | "She studied a lot, and didn't pass."      || "Third-person singular feminine (She)"
        "She is smart, but modest."                   | "She is smart, and modest."                || "Third-person singular feminine (She)"
        "She wants to help, but can't."               | "She wants to help, and can't."            || "Third-person singular feminine (She)"
        "She sings beautifully, but rarely."          | "She sings beautifully, and rarely."       || "Third-person singular feminine (She)"
        "She tried, but failed."                      | "She tried, and failed."                   || "Third-person singular feminine (She)"
        "She is friendly, but shy."                   | "She is friendly, and shy."                || "Third-person singular feminine (She)"

        // Third-person plural (They)
        "They were ready, but the event was canceled."| "They were ready, and the event was canceled." || "Third-person plural (They)"
        "They want to join, but need permission."     | "They want to join, and need permission."   || "Third-person plural (They)"
        "They acted quickly, but efficiently."        | "They acted quickly, and efficiently."      || "Third-person plural (They)"
        "They agreed, but not entirely."              | "They agreed, and not entirely."            || "Third-person plural (They)"
        "They came early, but left late."             | "They came early, and left late."           || "Third-person plural (They)"
        "They won the game, but lost the series."     | "They won the game, and lost the series."   || "Third-person plural (They)"

        // First-person plural (We)
        "We planned well, but things went wrong."     | "We planned well, and things went wrong."   || "First-person plural (We)"
        "We want to help, but lack resources."        | "We want to help, and lack resources."      || "First-person plural (We)"
        "We tried hard, but didn't succeed."          | "We tried hard, and didn't succeed."        || "First-person plural (We)"
        "We are excited, but nervous."                | "We are excited, and nervous."              || "First-person plural (We)"
        "We agreed, but with reservations."           | "We agreed, and with reservations."         || "First-person plural (We)"
        "We arrived early, but the event was delayed."| "We arrived early, and the event was delayed." || "First-person plural (We)"

        // Third-person singular neutral (It)
        "It looks good, but feels cheap."             | "It looks good, and feels cheap."           || "Third-person singular neutral (It)"
        "It works, but not perfectly."                | "It works, and not perfectly."              || "Third-person singular neutral (It)"
        "It started late, but finished on time."      | "It started late, and finished on time."    || "Third-person singular neutral (It)"
        "It seems simple, but is complicated."        | "It seems simple, and is complicated."      || "Third-person singular neutral (It)"
        "It was fun, but tiring."                     | "It was fun, and tiring."                   || "Third-person singular neutral (It)"
        "It rains, but not heavily."                  | "It rains, and not heavily."                || "Third-person singular neutral (It)"
    }
}