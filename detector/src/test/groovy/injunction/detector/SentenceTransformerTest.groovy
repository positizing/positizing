package injunction.detector

import injuction.detector.DesktopTaskExecutor
import injuction.detector.InjunctionDetector
import injuction.detector.TaskExecutor
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

        def "transformSentence should correctly transform simple sentences"() {
            given:
            String testSentence = "She makes me feel happy."
            String expectedTransformed = "I feel happy with her."
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
    }