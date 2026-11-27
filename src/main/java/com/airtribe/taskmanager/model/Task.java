package com.airtribe.taskmanager.model;

public class Task {
    private int id;
    private String title;
    private String description;
    private boolean completed;
    private String dueDate;
    private Priority priority;
    private int parentId;
    private long createdAt;

    private Task(TaskBuilder taskBuilder) {
        this.id = taskBuilder.id;
        this.title = taskBuilder.title;
        this.description = taskBuilder.description;
        this.completed = taskBuilder.completed;
        this.dueDate = taskBuilder.dueDate;
        this.priority = taskBuilder.priority;
        this.parentId = taskBuilder.parentId;
        this.createdAt = taskBuilder.createdAt;
    }

    //#region Getters

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCompleted() {
        return completed;
    }

    public String getDueDate() {
        return dueDate;
    }

    public Priority getPriority() {
        return priority;
    }

    public int getParentId() {
        return parentId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    // #endregion

    public static class TaskBuilder {
        private int id;
        private String title;
        private String description;
        private boolean completed;
        private String dueDate;
        private Priority priority;
        private int parentId;
        private long createdAt;

        public TaskBuilder(String title, String description, boolean completed) {
            this.title = title;
            this.description = description;
            this.completed = completed;
        }

        public TaskBuilder setId(int id) {
            this.id = id;
            return this;
        }

        public TaskBuilder setDueDate(String dueDate) {
            this.dueDate = dueDate;
            return this;
        }

        public TaskBuilder setParentId(int parentId) {
            this.parentId = parentId;
            return this;
        }

        public TaskBuilder setPriority(Priority priority) {
            this.priority = priority;
            return this;
        }

        public TaskBuilder setCreatedAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Task build() {
            if (this.createdAt == 0) {
                this.createdAt = System.currentTimeMillis();
            }
            return new Task(this);
        }
    }
}
