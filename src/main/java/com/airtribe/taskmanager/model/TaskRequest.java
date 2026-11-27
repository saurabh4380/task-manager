package com.airtribe.taskmanager.model;

import java.util.Optional;

public class TaskRequest {
    private String title;
    private String description;
    private boolean completed;
    private String dueDate;
    private String priority;
    private Optional<Integer> parentTaskId;

    public Optional<Integer> getParentTaskId() {
        return parentTaskId;
    }

    public void setParentTaskId(Optional<Integer> parentTaskId) {
        this.parentTaskId = parentTaskId;
    }

    // #region Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    // #endregion
}
