package com.airtribe.taskmanager.service.sort;

import java.util.List;

/**
 * Registration point for the default sort strategies. Add a new
 * {@link SortStrategy} implementation to {@link #DEFAULTS} and it is picked up
 * automatically; no existing strategy or query code needs to change.
 */
public final class TaskSorts {

    private static final List<SortStrategy> DEFAULTS = List.of(
            new CreatedAtAscendingSort(),
            new CreatedAtDescendingSort(),
            new DueDateAscendingSort(),
            new DueDateDescendingSort());

    private TaskSorts() {
    }

    public static List<SortStrategy> defaults() {
        return DEFAULTS;
    }
}
