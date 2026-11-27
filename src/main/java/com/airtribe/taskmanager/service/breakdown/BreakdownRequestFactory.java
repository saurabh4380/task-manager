package com.airtribe.taskmanager.service.breakdown;

import java.util.Optional;

import com.airtribe.taskmanager.model.Priority;
import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskRequest;
import com.airtribe.taskmanager.service.DateHelper;

/** Builds valid default values for a subtask request derived from its parent. */
final class BreakdownRequestFactory {

    private BreakdownRequestFactory() {
    }

    static TaskRequest create(Task parent, String title, String description) {
        var request = new TaskRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setPriority(parent.getPriority() == null
                ? Priority.MEDIUM.name()
                : parent.getPriority().name());
        request.setDueDate(DateHelper.isValidDate(parent.getDueDate())
                ? parent.getDueDate()
                : DateHelper.today());
        request.setParentTaskId(Optional.of(parent.getId()));
        return request;
    }
}
