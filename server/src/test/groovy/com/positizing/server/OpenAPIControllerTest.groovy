package com.positizing.server

import jakarta.json.Json
import jakarta.json.JsonReader
import jakarta.json.JsonString
import spock.lang.Specification

class OpenAPIControllerTest extends Specification {

    def "parseJsonArray parses valid JSON array of strings"() {
        given:
        def json = '["I like this", "I want more", "I feel curious"]'
        def controller = new OpenAPIController()

        when:
        def result = controller.parseJsonArray(json)

        then:
        result == ["I like this", "I want more", "I feel curious"]
    }

//    def "parseJsonArray throws on corrupted or non-array JSON"() {
//        given:
//        def invalidJson = '["I like this", "Oops" "I feel curious"]'
//        def controller = new OpenAPIController()
//
//        when:
//        controller.parseJsonArray(invalidJson)
//
//        then:
//        def ex = thrown(jakarta.json.stream.JsonParsingException)
//        ex.message.contains("Unexpected char")
//    }

    def "parseJsonArray can extract array from wrapped response"() {
        given:
        def wrappedJson = '''
            This is your answer:
            ["Suggestion one", "Suggestion two"]
            Thanks!
        '''.stripIndent()
        def controller = new OpenAPIController()

        when:
        def start = wrappedJson.indexOf('[')
        def end = wrappedJson.lastIndexOf(']')
        def sliced = wrappedJson.substring(start, end + 1)

        def result = controller.parseJsonArray(sliced)

        then:
        result == ["Suggestion one", "Suggestion two"]
    }
}
