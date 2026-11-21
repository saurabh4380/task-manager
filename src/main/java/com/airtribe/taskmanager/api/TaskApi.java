package com.airtribe.taskmanager.api;

import com.airtribe.taskmanager.exception.NotFoundException;
import com.airtribe.taskmanager.exception.ValidationException;
import com.airtribe.taskmanager.model.ApiResponse;
import com.airtribe.taskmanager.model.TaskQuery;
import com.airtribe.taskmanager.model.TaskRequest;
import com.airtribe.taskmanager.repository.TaskRepository;
import com.airtribe.taskmanager.service.TaskService;

public class TaskApi {

    private final TaskService taskService;

    public TaskApi(TaskService taskService, TaskRepository taskRepository) {
        this.taskService = new TaskService(taskRepository);
    }

    public ApiResponse createTask(TaskRequest request, String idempotencyKey) {

        try {
            var task = taskService.createTask(request, idempotencyKey);
            return new ApiResponse(201, task);

        } catch (ValidationException exception) {
            return ErrorResponseFactory.from(exception);
        } catch (Exception exception) {
            return ErrorResponseFactory.fromUnexpected(exception);
        }
    }

    public ApiResponse getTask(int id) {
        try {
            var task = taskService.getTask(id);
            return new ApiResponse(200, task);

        } catch (NotFoundException exception) {
            return ErrorResponseFactory.from(exception);
        } catch (Exception exception) {
            return ErrorResponseFactory.fromUnexpected(exception);
        }
    }

    public ApiResponse listTasks(TaskQuery query) {
        try {
            var tasks = taskService.listTasks(query);
            return new ApiResponse(200, tasks);

        } catch (Exception exception) {
            return ErrorResponseFactory.fromUnexpected(exception);
        }
    }

    public ApiResponse updateTask(int id, TaskRequest request) {
        try {
            var task = taskService.updateTask(id, request);
            return new ApiResponse(200, task);

        } catch (NotFoundException exception) {
            return ErrorResponseFactory.from(exception);
        } catch (ValidationException exception) {
            return ErrorResponseFactory.from(exception);
        } catch (Exception exception) {
            return ErrorResponseFactory.fromUnexpected(exception);
        }
    }

    public ApiResponse deleteTask(int id) {
        try {
            var task = taskService.deleteTask(id);
            return new ApiResponse(200, task);

        } catch (NotFoundException exception) {
            return ErrorResponseFactory.from(exception);
        } catch (Exception exception) {
            return ErrorResponseFactory.fromUnexpected(exception);
        }
    }

}
