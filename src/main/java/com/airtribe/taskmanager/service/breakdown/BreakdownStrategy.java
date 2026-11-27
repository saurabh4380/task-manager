package com.airtribe.taskmanager.service.breakdown;

import java.util.List;

import com.airtribe.taskmanager.exception.BadGatewayException;
import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskRequest;

/**
 * Produces the subtask requests for a task. Implementations must not persist
 * anything: the service validates every request before creating any of them.
 */
public interface BreakdownStrategy {

    List<TaskRequest> breakDown(Task task) throws BadGatewayException;
}
