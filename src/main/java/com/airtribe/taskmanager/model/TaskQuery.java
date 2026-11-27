package com.airtribe.taskmanager.model;

/**
 * Optional criteria used when listing tasks.
 *
 * <p>Every field is optional, so instances are created with the builder:
 *
 * <pre>{@code
 * TaskQuery query = TaskQuery.builder()
 *         .completed(false)
 *         .priority("HIGH")
 *         .search("milk")
 *         .sortBy("dueDate")
 *         .order("asc")
 *         .page(1)
 *         .pageSize(10)
 *         .build();
 * }</pre>
 */
public class TaskQuery {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;

    private Boolean completed;
    private String priority;
    private String search;
    private String sortBy;
    private String order;
    private int page = DEFAULT_PAGE;
    private int pageSize = DEFAULT_PAGE_SIZE;

    public TaskQuery() {
    }

    private TaskQuery(Builder builder) {
        this.completed = builder.completed;
        this.priority = builder.priority;
        this.search = builder.search;
        this.sortBy = builder.sortBy;
        this.order = builder.order;
        this.page = builder.page;
        this.pageSize = builder.pageSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** null means "do not filter by completion status". */
    public Boolean getCompleted() {
        return completed;
    }

    /** Alias for {@link #getCompleted()}. */
    public Boolean isCompleted() {
        return completed;
    }

    /** null/blank means "do not filter by priority". */
    public String getPriority() {
        return priority;
    }

    /** null/blank means "do not search". */
    public String getSearch() {
        return search;
    }

    /** "created" or "dueDate"; null/blank means "keep repository order". */
    public String getSortBy() {
        return sortBy;
    }

    /** "asc" or "desc"; null/blank defaults to "asc". */
    public String getOrder() {
        return order;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public static class Builder {

        private Boolean completed;
        private String priority;
        private String search;
        private String sortBy;
        private String order;
        private int page = DEFAULT_PAGE;
        private int pageSize = DEFAULT_PAGE_SIZE;

        public Builder() {
        }

        public Builder completed(boolean completed) {
            this.completed = completed;
            return this;
        }

        public Builder completed(Boolean completed) {
            this.completed = completed;
            return this;
        }

        public Builder setCompleted(boolean completed) {
            return completed(completed);
        }

        public Builder setCompleted(Boolean completed) {
            return completed(completed);
        }

        public Builder priority(String priority) {
            this.priority = priority;
            return this;
        }

        public Builder priority(Priority priority) {
            this.priority = priority == null ? null : priority.name();
            return this;
        }

        public Builder setPriority(String priority) {
            return priority(priority);
        }

        public Builder setPriority(Priority priority) {
            return priority(priority);
        }

        public Builder search(String search) {
            this.search = search;
            return this;
        }

        public Builder setSearch(String search) {
            return search(search);
        }

        public Builder sortBy(String sortBy) {
            this.sortBy = sortBy;
            return this;
        }

        public Builder setSortBy(String sortBy) {
            return sortBy(sortBy);
        }

        public Builder order(String order) {
            this.order = order;
            return this;
        }

        public Builder setOrder(String order) {
            return order(order);
        }

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public Builder setPage(int page) {
            return page(page);
        }

        public Builder pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Builder setPageSize(int pageSize) {
            return pageSize(pageSize);
        }

        public TaskQuery build() {
            return new TaskQuery(this);
        }
    }
}
