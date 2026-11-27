package com.airtribe.taskmanager.service.breakdown;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.airtribe.taskmanager.exception.BadGatewayException;
import com.airtribe.taskmanager.exception.FieldIssue;
import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskRequest;
import com.airtribe.taskmanager.parser.LlmClient;

/**
 * Asks the model for subtasks in a fixed, line-oriented format:
 *
 * <pre>title|description</pre>
 *
 * <p>The reply is parsed with plain String operations. Every line is checked
 * before any request is returned; one malformed line fails the whole
 * breakdown with a 502.
 */
public class LlmBreakdownStrategy implements BreakdownStrategy {

    private final LlmClient llmClient;

    public LlmBreakdownStrategy(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public List<TaskRequest> breakDown(Task task) throws BadGatewayException {
        if (llmClient == null) {
            throw new BadGatewayException("LLM client is not configured");
        }

        String reply;
        try {
            reply = llmClient.sendRaw(buildPrompt(task));
        } catch (IOException exception) {
            throw new BadGatewayException("Unable to make LLM API call: " + exception.getMessage());
        }

        return parse(task, reply);
    }

    private static String buildPrompt(Task task) {
        return "Break the following task into smaller subtasks.\n"
                + "Reply with ONLY one subtask per line in this exact format:\n"
                + "title|description\n"
                + "Do not add numbering, bullets, headings, markdown, or code fences.\n"
                + "Return at least two subtasks.\n\n"
                + "Task title: " + safe(task.getTitle()) + "\n"
                + "Task description: " + safe(task.getDescription()) + "\n";
    }

    private static List<TaskRequest> parse(Task parent, String reply) throws BadGatewayException {
        if (reply == null || reply.isBlank()) {
            throw new BadGatewayException("LLM returned an empty breakdown");
        }

        var requests = new ArrayList<TaskRequest>();
        var issues = new ArrayList<FieldIssue>();

        for (String rawLine : reply.split("\\R")) {
            String line = normalize(rawLine);
            if (line == null) {
                continue;
            }

            int separator = line.indexOf('|');
            if (separator < 0) {
                issues.add(new FieldIssue("subtasks", "Invalid subtask line: " + line));
                continue;
            }

            String title = line.substring(0, separator).trim();
            String description = line.substring(separator + 1).trim();

            if (title.isEmpty()) {
                issues.add(new FieldIssue("title", "Subtask title cannot be empty"));
            }
            if (description.isEmpty()) {
                issues.add(new FieldIssue("description", "Subtask description cannot be empty"));
            }

            if (!title.isEmpty() && !description.isEmpty()) {
                requests.add(BreakdownRequestFactory.create(parent, title, description));
            }
        }

        if (!issues.isEmpty()) {
            throw new BadGatewayException("LLM returned invalid subtasks", issues);
        }

        if (requests.isEmpty()) {
            throw new BadGatewayException("LLM returned no subtasks");
        }

        return requests;
    }

    /** Trims whitespace, drops code fences/blank lines, and strips simple bullets/numbering. */
    private static String normalize(String rawLine) {
        if (rawLine == null) {
            return null;
        }

        String line = rawLine.trim();

        if (line.isEmpty() || line.startsWith("```")) {
            return null;
        }

        if (line.startsWith("- ") || line.startsWith("* ")) {
            line = line.substring(2).trim();
        }

        int digits = 0;
        while (digits < line.length() && Character.isDigit(line.charAt(digits))) {
            digits++;
        }
        if (digits > 0 && digits < line.length()
                && (line.charAt(digits) == '.' || line.charAt(digits) == ')')) {
            line = line.substring(digits + 1).trim();
        }

        return line.isEmpty() ? null : line;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
