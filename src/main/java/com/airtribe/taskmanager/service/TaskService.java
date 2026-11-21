package com.airtribe.taskmanager.service;

import java.util.ArrayList;
import java.util.List;

import com.airtribe.taskmanager.exception.FieldIssue;
import com.airtribe.taskmanager.exception.NotFoundException;
import com.airtribe.taskmanager.exception.ValidationException;
import com.airtribe.taskmanager.model.Priority;
import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskQuery;
import com.airtribe.taskmanager.model.TaskRequest;
import com.airtribe.taskmanager.repository.TaskRepository;
import com.airtribe.taskmanager.utils.IdGenerator;
import com.airtribe.taskmanager.utils.Validator;
import com.airtribe.taskmanager.model.Task.TaskBuilder;

public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Task createTask(TaskRequest request, String idempotencyKey) throws ValidationException {
        validateTaskRequest(request);

        var taskBuilder = new TaskBuilder(request.getTitle(), request.getDescription(), false);

        taskBuilder.setId(IdGenerator.getNextTaskId())
                .setPriority(Priority.MEDIUM);

        var task = taskRepository.upsert(taskBuilder.build());

        return task;
    }

    public Task getTask(int id) throws NotFoundException {
        var task = taskRepository.findById(id);

        if (task.isEmpty()) {
            throw new NotFoundException(String.format("Task with Id %s not found", id));
        } else {
            return task.get();
        }

    }

    public List<Task> listTasks(TaskQuery query) {
        return taskRepository.findAll();

    }

    public Task updateTask(int id, TaskRequest request) throws NotFoundException, ValidationException {

        validateTaskRequest(request);
        var task = getTask(id);

        var taskBuilder = new TaskBuilder(request.getTitle(), request.getDescription(), request.isCompleted());

        taskBuilder.setId(task.getId())
                .setDueDate(request.getDueDate())
                .setPriority(Enum.valueOf(Priority.class, request.getPriority()));

        // TODO: Support ParentId updates

        return taskRepository.upsert(taskBuilder.build());
    }

    public boolean deleteTask(int id) throws NotFoundException {
        var task = getTask(id);
        return taskRepository.delete(task.getId());
    }

    void validateTaskRequest(TaskRequest taskRequest) throws ValidationException {

        if (taskRequest == null) {
            throw new ValidationException("TaskRequest", "TaskRequest cannot be null");
        }

        var fieldIssues = new ArrayList<FieldIssue>();

        if (Validator.isNullOrEmpty(taskRequest.getTitle())) {
            fieldIssues.add(new FieldIssue("title", "Title cannot be null or empty"));
        }

        if (Validator.isNullOrEmpty(taskRequest.getDescription())) {
            fieldIssues.add(new FieldIssue("description", "Description cannot be null or empty"));

        }

        if (Validator.isNullOrEmpty(taskRequest.getPriority())
                || !Validator.isInEnum(Priority.class, taskRequest.getPriority())) {

            fieldIssues.add(new FieldIssue("priority", "Priority is invalid"));

        }

        if (!Validator.isValidDate(taskRequest.getDueDate())) {
            fieldIssues.add(new FieldIssue("dueDate", "DueDate is invalid"));

        }

        if (!fieldIssues.isEmpty()) {
            throw new ValidationException(fieldIssues);
        }

    }

}
