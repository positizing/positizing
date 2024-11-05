package injunction.detector

import injuction.detector.InjunctionDetector
import spock.lang.Specification

class InjunctionDetectorTest extends Specification {

    InjunctionDetector detector

    void setup() {
        detector = new InjunctionDetector()
    }

    def "Detector can find simple 'not' phrases"() {
        expect:
        detector.isInjuction("You don’t have time for distractions")
    }

    def "Detector works on a wide variety of phrases containing injunctions"(String testText) {
        expect:
        detector.isInjuction(testText)

        where:
        testText << [
                // Original test cases with 'not', 'never', 'can't', etc.
                "I will never read this",
                "I do not like reading this",
                "I'm not contradicting you",
                "It's not the best for you",
                "This isn't working",
                "You don’t have time for distractions",
                // ... (include all your previous test cases here)
                "I can't do this anymore",
                "Don't let me down",
                "I shouldn't have done that",
                "He won't be coming",
        ]
    }

    def "Detector can find phrases containing 'should'"(String testText) {
        expect:
        detector.isInjuction(testText)

        where:
        testText << [
                "I should really read this",
                "I shouldn't do this",
                "You should listen to me",
                "They should not have left early",
                "We should consider all options",
        ]
    }

    def "Detector can find phrases containing 'but'"(String testText) {
        expect:
        detector.isInjuction(testText)

        where:
        testText << [
                "I wanted to go, but it was too late.",
                "She tried her best, but failed.",
                "He is smart, but lazy.",
                "We could go, but I don't want to.",
                "It's a good idea, but it's not practical.",
        ]
    }

    def "Detector does not detect injunctions in neutral phrases"(String testText) {
        expect:
        !detector.isInjuction(testText)

        where:
        testText << [
                "This is a clean sentence.",
                "I went to the store yesterday.",
                "She enjoys reading books.",
                "He plays soccer on weekends.",
                "They are planning a trip.",
        ]
    }

    def "Detector correctly identifies phrases without 'but' as non-injunctions"(String testText) {
        expect:
        !detector.isInjuction(testText)

        where:
        testText << [
                "I like apples and oranges.",
                "She sings and dances.",
                "We will go when it's sunny.",
                "He wants to learn more.",
                "They decided to stay.",
        ]
    }

    def "Detector handles phrases with 'but' that are not injunctions"(String testText) {
        expect:
        !detector.isInjuction(testText)

        where:
        testText << [
                "I like apples but also enjoy oranges.",
                "He is not only smart but also kind.",
                "She is young but very experienced.",
                "They are rich but humble.",
                "It's challenging but rewarding.",
        ]
    }

    def "Detector correctly identifies complex phrases with expected results"(String testText, boolean expected) {
        expect:
        detector.isInjuction(testText) == expected

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

    def "Detector handles phrases with both injunctions and profanity"(String testText, boolean expectedInjunction, boolean expectedProfanity) {
        expect:
        detector.isInjuction(testText) == expectedInjunction
        detector.containsProfanity(testText) == expectedProfanity

        where:
        testText                                || expectedInjunction || expectedProfanity
        "I can't believe this happened!"        || true               || false
        "He is a brilliant individual."         || false              || false
        "This is an unacceptable situation."    || false              || false
        "Don't let them stop you."              || true               || false
        "She won't be able to make it."         || true               || false
        "We are going to succeed."              || false              || false
        // Note: Replace '[profane word]' with an actual profane word from your list if appropriate
        "This is absolutely bitchin!"    || false              || true
    }

    def "Detector does not detect injunctions in phrases with 'but' used positively"(String testText) {
        expect:
        !detector.isInjuction(testText)

        where:
        testText << [
                "She is not only talented but also hardworking.",
                "The cake was sweet but not too sweet.",
                "It's difficult but achievable.",
                "He is young but very wise.",
                "They moved quickly but silently.",
        ]
    }

    def "Detector handles edge cases for 'not' and 'but'"(String testText, boolean expected) {
        expect:
        detector.isInjuction(testText) == expected

        where:
        testText                                        || expected
        "Not everything is lost."                       || true
        "But for the grace of God."                     || true
        "He is neither rich nor famous."                || false
        "I can't not go."                               || true
        "But wait, there's more!"                       || true
    }

    def "Detector correctly identifies phrases with negations and conjunctions"(String testText, boolean expected) {
        expect:
        detector.isInjuction(testText) == expected

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

}