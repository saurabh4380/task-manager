package com.airtribe.taskmanager.parser;

import java.io.IOException;

import tools.jackson.databind.ObjectMapper;

public class LlmTaskParse implements TaskParser {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public LlmTaskParse(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ParsedTask parse(String text, String today) throws IOException {

        var llmResponse = llmClient.send(text);

        var parsedtask = objectMapper.readValue(llmResponse, ParsedTask.class);

        return parsedtask;
    }

}