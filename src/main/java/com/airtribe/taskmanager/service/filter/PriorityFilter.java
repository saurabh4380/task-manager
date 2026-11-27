package com.airtribe.taskmanager.service.filter;

import java.util.Locale;
import java.util.function.Predicate;

import com.airtribe.taskmanager.exception.ValidationException;
import com.airtribe.taskmanager.model.Priority;
import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskQuery;

/** Keeps only tasks with the requested priority. */
public class PriorityFilter implements TaskFilter {

    @Override
    public boolean supports(TaskQuery query) {
        return query.getPriority() != null && !query.getPriority().isBlank();
    }

    @Override
    public Predicate<Task> predicate(TaskQuery query) {
        Priority priority;

        try {
            priority = Priority.valueOf(query.getPriority().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("priority", "Priority is invalid");
        }

        return task -> task.getPriority() == priority;
    }
}
