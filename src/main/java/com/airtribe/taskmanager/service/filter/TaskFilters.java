package com.airtribe.taskmanager.service.filter;

import java.util.List;

/**
 * Registration point for the default filter set. Add a new {@link TaskFilter}
 * implementation to {@link #DEFAULTS} and it is picked up automatically; no
 * existing filter or query code needs to change.
 */
public final class TaskFilters {

    private static final List<TaskFilter> DEFAULTS = List.of(
            new CompletedFilter(),
            new PriorityFilter(),
            new SearchFilter());

    private TaskFilters() {
    }

    public static List<TaskFilter> defaults() {
        return DEFAULTS;
    }
}
