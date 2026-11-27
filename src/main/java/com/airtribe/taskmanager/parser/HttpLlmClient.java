package com.airtribe.taskmanager.parser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.airtribe.taskmanager.service.DateHelper;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 *
 * Real LlmClient that makes one HTTPS call with a timeout. Supports three
 * providers so
 * each student can use whichever key they have. Request formats were checked
 * against each
 * provider's API reference (see LLM_API_CHEATSHEET.md in the assignment
 * folder).
 *
 * provider endpoint key sent as reply text found at
 * "anthropic" POST api.anthropic.com/v1/messages x-api-key header
 * content[0].text
 * "openai" POST api.openai.com/v1/chat/completions Authorization: Bearer
 * choices[0].message.content
 * "gemini" POST generativelanguage.googleapis.com/v1beta/ x-goog-api-key header
 * candidates[0].content.parts[0].text
 * models/{model}:generateContent
 *
 * Environment variables (read by the CALLER, e.g. ParserFactory, NOT by this
 * class):
 * LLM_API_KEY required for the real model; if missing, answer 503
 * LLM_PROVIDER optional, default "anthropic" ("anthropic" | "openai" |
 * "gemini")
 * LLM_MODEL optional, overrides the default model below
 *
 * Default models are only defaults. Providers retire model names every few
 * months: if you
 * get "LLM provider returned HTTP 404" (or 400), set LLM_MODEL to a model from
 * your provider's
 * model list.
 *
 * When you copy this into your own project, change the package line to: package
 * parser;
 */
public class HttpLlmClient implements LlmClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String apiKey;
    private final String provider;
    private final String model;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper mapper;

    public HttpLlmClient(String apiKey, String provider, String model) {
        this(apiKey, provider, model, null);
    }

    /**
     * baseUrl is only for tests (point it at a local fake server). Leave null for
     * the real provider.
     */
    public HttpLlmClient(String apiKey, String provider, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.provider = provider == null || provider.isBlank() ? "deepseek" : provider.trim().toLowerCase();
        this.model = model == null || model.isBlank() ? defaultModel(this.provider) : model.trim();
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? defaultBaseUrl(this.provider) : baseUrl;

        this.mapper = new ObjectMapper();

    }

    @Override
    public String send(String prompt) throws IOException {
        HttpRequest request;
        try {
            request = buildRequest(prompt);
        } catch (IllegalArgumentException e) {
            // e.g. a LLM_MODEL containing spaces makes the URL invalid
            throw new IOException("Invalid LLM configuration (check LLM_MODEL): " + e.getMessage(), e);
        }
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("LLM call interrupted", e);
        }
        if (response.statusCode() / 100 != 2) {
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                // Do not echo the provider's text: some providers print part of the key you
                // sent.
                throw new IOException("LLM provider rejected the key (HTTP " + status
                        + "): check LLM_API_KEY and LLM_PROVIDER");
            }
            // All three providers put a readable explanation in a "message" field of the
            // error body.
            String detail = SimpleJson.firstStringValue(response.body(), "message");
            throw new IOException("LLM provider returned HTTP " + status + (detail == null ? "" : ": " + detail));
        }
        String textKey = provider.equals("openai") || provider.equals("deepseek") ? "content" : "text";
        String reply = SimpleJson.firstStringValue(response.body(), textKey);
        if (reply == null) {
            throw new IOException("LLM response contained no text");
        }
        return reply;
    }

    private HttpRequest buildRequest(String prompt) throws IOException {
        String quoted = SimpleJson.quote(prompt);
        HttpRequest.Builder b = HttpRequest.newBuilder().timeout(TIMEOUT)
                .header("Content-Type", "application/json");
        switch (provider) {
            case "anthropic":
                // max_tokens is REQUIRED by this API.
                return b.uri(URI.create(baseUrl + "/v1/messages"))
                        .header("x-api-key", apiKey)
                        .header("anthropic-version", "2023-06-01")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                buildRequestBody(quoted)))
                        .build();
            case "openai":
                return b.uri(URI.create(baseUrl + "/v1/chat/completions"))
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"model\":" + SimpleJson.quote(model)
                                        + ",\"messages\":[{\"role\":\"user\",\"content\":" + quoted + "}]}"))
                        .build();
            case "deepseek":
                return b.uri(URI.create(baseUrl + "/chat/completions"))
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                buildRequestBody(quoted)))
                        .build();
            case "gemini":
                return b.uri(URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"))
                        .header("x-goog-api-key", apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"contents\":[{\"parts\":[{\"text\":" + quoted + "}]}]}"))
                        .build();
            default:
                throw new IOException("Unknown LLM_PROVIDER: " + provider + " (use anthropic, openai or gemini)");
        }
    }

    private String buildRequestBody(String quoted) {

        // 2. A prompt that pins the output format and gives the model today's date.
        String today = DateHelper.today();

        String systemPrompt = "Today's date is " + today + " (format YYYY-MM-DD).\n"
                + "Turn the user's text into ONE task.\n"
                + "Reply with ONLY a JSON object, no explanation, no code fences, with exactly these fields:\n"
                + "If you cannot determine the dueDate from the text then set it as today's date.\n"
                + "  \"title\": short string, required\n"
                + "  \"description\": string, required\n"
                + "  \"dueDate\": YYYY-MM-DD or null\n"
                + "  \"recurrence\": string such as \"monthly\", or null\n";

        String userInput = quoted;

        // Build request payload safely with Jackson
        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", model);
        payload.put("max_tokens", 1024);
        payload.put("temperature", 0.0);

        // Disable thinking mode to minimize token usage
        payload.putObject("thinking").put("type", "disabled");

        // Enforce JSON output
        payload.putObject("response_format").put("type", "json_object");

        ArrayNode messages = payload.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", systemPrompt);
        messages.addObject()
                .put("role", "user")
                .put("content", userInput);

        return mapper.writeValueAsString(payload);
    }

    private static String defaultBaseUrl(String provider) {
        switch (provider) {
            case "openai":
                return "https://api.openai.com";
            case "gemini":
                return "https://generativelanguage.googleapis.com";
            case "deepseek":
                return "https://api.deepseek.com";
            default:
                return "https://api.anthropic.com";
        }
    }

    private static String defaultModel(String provider) {
        switch (provider) {
            case "openai":
                return "gpt-6-luna";
            case "gemini":
                return "gemini-3.8-flash";
            case "deepseek":
                return "deepseek-flash";
            default:
                return "claude-haiku-5-5";
        }
    }
}
