package com.airtribe.taskmanager.exception;

import java.util.ArrayList;
import java.util.List;

public class ValidationException extends ApiException {

    private final List<FieldIssue> issues;

    public ValidationException(List<FieldIssue> issues) {
        super(400, "VALIDATION_ERROR", "Validation failed");
        this.issues = new ArrayList<>(issues);
    }

    /** Convenience for a single problem. */
    public ValidationException(String field, String issue) {
        this(List.of(new FieldIssue(field, issue)));
    }

    public List<FieldIssue> getIssues() {
        return new ArrayList<>(issues);
    }
}
