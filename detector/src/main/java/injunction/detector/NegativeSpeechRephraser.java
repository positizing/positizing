package injunction.detector;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * NegativeSpeechRephraser:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 12/11/2024 @ 7:34 p.m.
 */

public class NegativeSpeechRephraser {

    private static final String API_KEY = System.getProperty("OPENAPI_KEY", System.getenv("OPENAPI_KEY"));
    static {
        if (API_KEY == null) {
            throw new IllegalStateException("Missing required property OPENAPI_KEY. Set this environment variable to your openapi API key");
        }
    }

    //    private static final String API_URL = "https://api.openai.com/v1/completions";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    public static List<String> rephraseNegativeSpeech(String userInput) throws IOException {
        OkHttpClient client = new OkHttpClient();

        // Construct the system prompt and user message
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are an expert in transforming negative speech into positive, empowering language based on Eric Berne's Transactional Analysis and Dr. Hawkins' Scale of Consciousness. Follow the provided guidelines to rephrase the user's input.");

        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", String.format(
                "Please transform the following sentence into a few examples of positive, empowering language based on these guidelines:\n" +
                        "- Replace words like \"don't,\" \"isn't,\" \"can't,\" \"not,\" \"try,\" and \"should\" with positive alternatives.\n" +
                        "- Encourage ownership of feelings by rephrasing sentences starting with \"You make me feel...\" to \"I feel [emotion]...\"\n" +
                        "- Avoid using injunctions, conjunctions or other forms of negative language in responses\n" +
                        "- Replace \"I feel that...\" with \"I feel [emotion]...\"\n" +
                        "- Replace \"You make me feel...\" with \"I feel [emotion] because...\"\n\n" +
                        "Sentence: \"%s\"\n\n" +
                        "Return the results as a json array:", userInput));

        // Build the messages array
        JSONArray messages = new JSONArray();
        messages.put(systemMessage);
        messages.put(userMessage);

        // Build the JSON payload
        JSONObject json = new JSONObject();
        json.put("model", "gpt-4o-mini"); // Use "gpt-4"
        json.put("messages", messages);
        json.put("max_tokens", 150);
        json.put("temperature", 0.7);

        // Create the request body
        RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.parse("application/json")
        );

        // Build the HTTP request
        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + API_KEY)
                .post(body)
                .build();

        // Send the request and get the response
        Response response = client.newCall(request).execute();

        if (response.isSuccessful()) {
            String responseBody = response.body().string();
            JSONObject responseJson = new JSONObject(responseBody);
            // Extract the rephrased text from the response
            String rephrasedText = responseJson
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();
            JSONArray results = new JSONArray(rephrasedText.replaceAll("```?(json\\n)", ""));
            List<String> list = new ArrayList<>();
            for (int i = 0 ; i < results.length(); i++ ) {
                list.add(results.getString(i));
            }
            return list;
        } else {
            String errorBody = response.body().string();
            System.err.println("Error response: " + errorBody);
            throw new IOException("Unexpected response code " + response.code());
        }
    }

    // Example usage
    public static void main(String[] args) {
        String userSentence = "I feel that you don't understand me and it makes me feel sad.";
        try {
            List<String> positiveSentences = rephraseNegativeSpeech(userSentence);
            System.out.println("Positive Rephrased Versions:");
            for (Object result : positiveSentences) {
                System.out.println(result);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}