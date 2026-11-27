package com.airtribe.taskmanager.service.sort;

import java.util.Comparator;

import com.airtribe.taskmanager.model.Task;

/**
 * A single, self-contained task ordering.
 *
 * <p>Adding a new ordering only requires a new class and one registration
 * entry in {@link TaskSorts}.
 */
public interface SortStrategy {

    boolean supports(String sortBy, String order);

    Comparator<Task> comparator();
}
