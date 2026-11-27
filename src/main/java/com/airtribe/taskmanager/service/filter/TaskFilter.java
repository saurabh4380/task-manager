package com.airtribe.taskmanager.service.filter;

import java.util.function.Predicate;

import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskQuery;

/**
 * A single, self-contained filter criterion.
 *
 * <p>Implementations are stateless: {@link #supports(TaskQuery)} decides whether
 * the criterion applies to the query, and {@link #predicate(TaskQuery)} creates
 * the actual task predicate. Adding a new filter only requires a new class and
 * one registration entry in {@link TaskFilters}.
 */
public interface TaskFilter {

    boolean supports(TaskQuery query);

    Predicate<Task> predicate(TaskQuery query);
}
