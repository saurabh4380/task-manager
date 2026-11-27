package com.airtribe.taskmanager.service.sort;

import java.util.Comparator;

import com.airtribe.taskmanager.model.Task;

/** Earliest due date first; tasks without a due date are kept at the end. */
public class DueDateAscendingSort implements SortStrategy {

    @Override
    public boolean supports(String sortBy, String order) {
        return "dueDate".equalsIgnoreCase(sortBy) && "asc".equalsIgnoreCase(order);
    }

    @Override
    public Comparator<Task> comparator() {
        return Comparator.comparing(Task::getDueDate,
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(Task::getId);
    }
}
