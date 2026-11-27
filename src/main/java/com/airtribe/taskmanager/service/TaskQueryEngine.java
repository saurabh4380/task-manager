package com.airtribe.taskmanager.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.airtribe.taskmanager.exception.FieldIssue;
import com.airtribe.taskmanager.exception.ValidationException;
import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskQuery;
import com.airtribe.taskmanager.service.filter.TaskFilter;
import com.airtribe.taskmanager.service.filter.TaskFilters;
import com.airtribe.taskmanager.service.sort.SortStrategy;
import com.airtribe.taskmanager.service.sort.TaskSorts;

/**
 * Runs the full list pipeline: filter -> search -> sort -> paginate.
 *
 * <p>The engine never knows about individual criteria. It delegates to the
 * registered {@link TaskFilter} and {@link SortStrategy} implementations, so
 * new filters/sorts can be added without touching this class.
 */
public class TaskQueryEngine {

    private final List<TaskFilter> filters;
    private final List<SortStrategy> sortStrategies;

    public TaskQueryEngine() {
        this(TaskFilters.defaults(), TaskSorts.defaults());
    }

    public TaskQueryEngine(List<TaskFilter> filters, List<SortStrategy> sortStrategies) {
        this.filters = List.copyOf(filters);
        this.sortStrategies = List.copyOf(sortStrategies);
    }

    public List<Task> execute(List<Task> tasks, TaskQuery query) {
        if (query == null) {
            return new ArrayList<>(tasks);
        }

        validatePagination(query);

        Stream<Task> stream = tasks.stream();

        for (TaskFilter filter : filters) {
            if (filter.supports(query)) {
                stream = stream.filter(filter.predicate(query));
            }
        }

        SortStrategy sortStrategy = resolveSortStrategy(query);
        if (sortStrategy != null) {
            stream = stream.sorted(sortStrategy.comparator());
        }

        long offset = (long) (query.getPage() - TaskQuery.DEFAULT_PAGE) * query.getPageSize();

        return stream
                .skip(offset)
                .limit(query.getPageSize())
                .toList();
    }

    private void validatePagination(TaskQuery query) {
        var fieldIssues = new ArrayList<FieldIssue>();

        if (query.getPage() < TaskQuery.DEFAULT_PAGE) {
            fieldIssues.add(new FieldIssue("page", "Page must be greater than or equal to 1"));
        }

        if (query.getPageSize() < 1) {
            fieldIssues.add(new FieldIssue("pageSize", "PageSize must be greater than or equal to 1"));
        }

        if (!fieldIssues.isEmpty()) {
            throw new ValidationException(fieldIssues);
        }
    }

    private SortStrategy resolveSortStrategy(TaskQuery query) {
        String sortBy = query.getSortBy();

        if (sortBy == null || sortBy.isBlank()) {
            return null;
        }

        String order = query.getOrder() == null || query.getOrder().isBlank()
                ? "asc"
                : query.getOrder().trim();

        if (!"asc".equalsIgnoreCase(order) && !"desc".equalsIgnoreCase(order)) {
            throw new ValidationException("order", "Order must be either 'asc' or 'desc'");
        }

        for (SortStrategy sortStrategy : sortStrategies) {
            if (sortStrategy.supports(sortBy.trim(), order)) {
                return sortStrategy;
            }
        }

        throw new ValidationException("sortBy", "SortBy must be either 'created' or 'dueDate'");
    }
}
