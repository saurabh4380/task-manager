package com.airtribe.taskmanager.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.airtribe.taskmanager.exception.BadGatewayException;
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
import com.airtribe.taskmanager.parser.TaskParser;

public class TaskService {
    private final TaskRepository taskRepository;
    private final TaskParser taskParser;

    public TaskService(TaskRepository taskRepository, TaskParser taskParser) {
        this.taskRepository = taskRepository;
        this.taskParser = taskParser;
    }

    public Task createTask(TaskRequest request, String idempotencyKey) throws ValidationException {
        validateTaskRequest(request);

        var taskBuilder = new TaskBuilder(request.getTitle(), request.getDescription(), false);

        taskBuilder.setId(IdGenerator.getNextTaskId())
                .setPriority(Priority.MEDIUM);

        if (request.getParentTaskId().isPresent()) {
            taskBuilder.setParentId(request.getParentTaskId().get());
        }

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

        return taskRepository.upsert(taskBuilder.build());
    }

    public boolean deleteTask(int id) throws NotFoundException {
        var task = getTask(id);

        var childTasks = listSubtasks(id);

        for (Task cTask : childTasks) {
            taskRepository.delete(cTask.getId());
        }

        return taskRepository.delete(task.getId());
    }

    public Task createTaskFromText(String text, String idempotencyKey) throws BadGatewayException, ValidationException {

        try {
            var parsedTask = taskParser.parse(text, DateHelper.today());

            if (parsedTask == null) {
                throw new BadGatewayException("Unable to parse LLM response");
            }

            var taskRequest = new TaskRequest();
            taskRequest.setTitle(parsedTask.title());
            taskRequest.setDescription(parsedTask.description());
            taskRequest.setDueDate(parsedTask.dueDate());
            taskRequest.setPriority(Priority.MEDIUM.toString());

            return createTask(taskRequest, idempotencyKey);

        } catch (IOException e) {
            throw new BadGatewayException("Unable to make LLM API call");
        }

    }

    public Task createSubtask(TaskRequest taskRequest) {
        return createTask(taskRequest, null);

    }

    public List<Task> listSubtasks(int parentId) {

        return listTasks(null).stream().filter(x -> x.getParentId() == parentId).toList();

    }

    public List<Task> breakDownTask(int id, String idempotencyKey) {

        var task = getTask(id);
        if (task.getParentId() > 0) {
            throw new ValidationException("ParentId", "A subtask cannot be broken down into subtasks");
        }

        return List.of();
        // RuleBasedBreakdownStrategy (split on "and" or commas)
        // LlmBreakdownStrategy (ask AI to breakdown the given task. Ask the AI to
        // repond back with title|description format and breakdown the tasks). Validate
        // the response from model and respond back with 502 if model validation fails

        // #Validation rules
        // A task that is itself a subtask cannot be broken down or given subtasks.
        // Return 400.
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
