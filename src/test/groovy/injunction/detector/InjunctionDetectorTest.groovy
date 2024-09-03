package injunction.detector

import injuction.detector.InjunctionDetector
import spock.lang.Specification

class InjunctionDetectorTest extends Specification {

    InjunctionDetector detector

    void setup() {
        detector = new InjunctionDetector()
    }

    def "Detector can find simple not phrases"(String testText) {
        expect:
        detector.isInjuction(testText)

        where:
        testText << [
            "I will never read this",
            "I do not like reading this",
        ]
    }
    def "Detector can find simple should phrases"(String testText) {
        expect:
        detector.isInjuction(testText)

        where:
        testText << [
            "I should really read this",
        ]
    }
}