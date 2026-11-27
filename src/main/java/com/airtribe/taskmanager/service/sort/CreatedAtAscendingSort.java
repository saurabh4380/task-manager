package com.airtribe.taskmanager.service.sort;

import java.util.Comparator;

import com.airtribe.taskmanager.model.Task;

/** Oldest created task first. */
public class CreatedAtAscendingSort implements SortStrategy {

    @Override
    public boolean supports(String sortBy, String order) {
        return "created".equalsIgnoreCase(sortBy) && "asc".equalsIgnoreCase(order);
    }

    @Override
    public Comparator<Task> comparator() {
        return Comparator.comparingLong(Task::getCreatedAt)
                .thenComparingInt(Task::getId);
    }
}
