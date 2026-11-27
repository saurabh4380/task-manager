package com.airtribe.taskmanager.service.filter;

import java.util.function.Predicate;

import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskQuery;

/** Keeps only completed tasks, or only open tasks, when the query asks for it. */
public class CompletedFilter implements TaskFilter {

    @Override
    public boolean supports(TaskQuery query) {
        return query.getCompleted() != null;
    }

    @Override
    public Predicate<Task> predicate(TaskQuery query) {
        boolean completed = query.getCompleted();
        return task -> task.isCompleted() == completed;
    }
}
