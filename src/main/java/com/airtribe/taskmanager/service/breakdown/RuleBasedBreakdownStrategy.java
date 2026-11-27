package com.airtribe.taskmanager.service.breakdown;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskRequest;

/**
 * Offline breakdown: splits the task title (or, when the title has no
 * separator, the description) on the word "and" or on commas.
 */
public class RuleBasedBreakdownStrategy implements BreakdownStrategy {

    private static final Pattern SEPARATOR = Pattern.compile("(?i)\\s+and\\s+|\\s*,\\s*");

    @Override
    public List<TaskRequest> breakDown(Task task) {
        String[] parts = SEPARATOR.split(task.getTitle() == null ? "" : task.getTitle());

        if (parts.length <= 1 && task.getDescription() != null) {
            String[] descriptionParts = SEPARATOR.split(task.getDescription());
            if (descriptionParts.length > 1) {
                parts = descriptionParts;
            }
        }

        var requests = new ArrayList<TaskRequest>();

        for (String part : parts) {
            String title = part == null ? "" : part.trim();
            if (title.isEmpty()) {
                continue;
            }
            requests.add(BreakdownRequestFactory.create(task, title, task.getDescription()));
        }

        return requests;
    }
}
