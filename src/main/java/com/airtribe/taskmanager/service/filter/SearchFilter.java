package com.airtribe.taskmanager.service.filter;

import java.util.Locale;
import java.util.function.Predicate;

import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskQuery;

/** Case-insensitive substring match against a task's title or description. */
public class SearchFilter implements TaskFilter {

    @Override
    public boolean supports(TaskQuery query) {
        return query.getSearch() != null && !query.getSearch().isBlank();
    }

    @Override
    public Predicate<Task> predicate(TaskQuery query) {
        String needle = query.getSearch().trim().toLowerCase(Locale.ROOT);
        return task -> containsIgnoreCase(task.getTitle(), needle)
                || containsIgnoreCase(task.getDescription(), needle);
    }

    private static boolean containsIgnoreCase(String value, String lowerCaseNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerCaseNeedle);
    }
}
