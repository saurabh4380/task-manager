package com.airtribe.taskmanager.exception;

public class FieldIssue {

    private final String field;
    private final String issue;

    public FieldIssue(String field, String issue) {
        this.field = field;
        this.issue = issue;
    }

    public String getField() {
        return field;
    }

    public String getIssue() {
        return issue;
    }
}
