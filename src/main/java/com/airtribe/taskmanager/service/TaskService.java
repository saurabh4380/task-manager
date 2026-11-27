package com.airtribe.taskmanager.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.airtribe.taskmanager.exception.BadGatewayException;
import com.airtribe.taskmanager.exception.FieldIssue;
import com.airtribe.taskmanager.exception.NotFoundException;
import com.airtribe.taskmanager.exception.ValidationException;
import com.airtribe.taskmanager.model.Priority;
import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskQuery;
import com.airtribe.taskmanager.model.TaskRequest;
import com.airtribe.taskmanager.repository.TaskRepository;
import com.airtribe.taskmanager.service.breakdown.BreakdownStrategy;
import com.airtribe.taskmanager.service.breakdown.LlmBreakdownStrategy;
import com.airtribe.taskmanager.service.breakdown.RuleBasedBreakdownStrategy;
import com.airtribe.taskmanager.utils.IdGenerator;
import com.airtribe.taskmanager.utils.Validator;
import com.airtribe.taskmanager.model.Task.TaskBuilder;
import com.airtribe.taskmanager.parser.LlmTaskParse;
import com.airtribe.taskmanager.parser.TaskParser;

public class TaskService {
    private final TaskRepository taskRepository;
    private final TaskParser taskParser;
    private final TaskQueryEngine taskQueryEngine;
    private final IdempotencyStore idempotencyStore;
    private final BreakdownStrategy breakdownStrategy;

    public TaskService(TaskRepository taskRepository, TaskParser taskParser) {
        this(taskRepository, taskParser, new TaskQueryEngine(), IdempotencyStore.getInstance(),
                defaultBreakdownStrategy(taskParser));
    }

    public TaskService(TaskRepository taskRepository, TaskParser taskParser, TaskQueryEngine taskQueryEngine) {
        this(taskRepository, taskParser, taskQueryEngine, IdempotencyStore.getInstance(),
                defaultBreakdownStrategy(taskParser));
    }

    public TaskService(TaskRepository taskRepository, TaskParser taskParser, TaskQueryEngine taskQueryEngine,
            IdempotencyStore idempotencyStore) {
        this(taskRepository, taskParser, taskQueryEngine, idempotencyStore, defaultBreakdownStrategy(taskParser));
    }

    public TaskService(TaskRepository taskRepository, TaskParser taskParser, TaskQueryEngine taskQueryEngine,
            IdempotencyStore idempotencyStore, BreakdownStrategy breakdownStrategy) {
        this.taskRepository = taskRepository;
        this.taskParser = taskParser;
        this.taskQueryEngine = taskQueryEngine;
        this.idempotencyStore = idempotencyStore;
        this.breakdownStrategy = breakdownStrategy != null
                ? breakdownStrategy
                : new RuleBasedBreakdownStrategy();
    }

    private static BreakdownStrategy defaultBreakdownStrategy(TaskParser taskParser) {
        if (taskParser instanceof LlmTaskParse llmTaskParse) {
            return new LlmBreakdownStrategy(llmTaskParse.getLlmClient());
        }
        return new RuleBasedBreakdownStrategy();
    }

    public Task createTask(TaskRequest request, String idempotencyKey) throws ValidationException {
        var fingerprint = fingerprint(request);

        return idempotencyStore.execute(idempotencyKey, fingerprint, () -> createTaskInternal(request));
    }

    private Task createTaskInternal(TaskRequest request) throws ValidationException {
        validateTaskRequest(request);

        var taskBuilder = new TaskBuilder(request.getTitle(), request.getDescription(), false);

        taskBuilder.setId(IdGenerator.getNextTaskId())
                .setPriority(Enum.valueOf(Priority.class, request.getPriority()))
                .setDueDate(request.getDueDate());

        if (request.getParentTaskId() != null && request.getParentTaskId().isPresent()) {
            taskBuilder.setParentId(request.getParentTaskId().get());
        }

        var task = taskRepository.upsert(taskBuilder.build());

        return task;
    }

    private static String fingerprint(TaskRequest request) {
        if (request == null) {
            return IdempotencyStore.fingerprint("createTask", null);
        }

        var parentTaskId = request.getParentTaskId() == null
                ? null
                : request.getParentTaskId().orElse(null);

        return IdempotencyStore.fingerprint(
                "createTask",
                request.getTitle(),
                request.getDescription(),
                request.isCompleted(),
                request.getDueDate(),
                request.getPriority(),
                parentTaskId);
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
        var tasks = taskRepository.findAll();

        if (query == null) {
            return new ArrayList<>(tasks);
        }

        return taskQueryEngine.execute(tasks, query);
    }

    public Task updateTask(int id, TaskRequest request) throws NotFoundException, ValidationException {

        validateTaskRequest(request);
        var task = getTask(id);

        var taskBuilder = new TaskBuilder(request.getTitle(), request.getDescription(), request.isCompleted());

        taskBuilder.setId(task.getId())
                .setParentId(task.getParentId())
                .setCreatedAt(task.getCreatedAt())
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

        var fingerprint = IdempotencyStore.fingerprint("createTaskFromText", text);

        return idempotencyStore.execute(idempotencyKey, fingerprint, () -> {
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

                return createTaskInternal(taskRequest);

            } catch (IOException e) {
                throw new BadGatewayException("Unable to make LLM API call");
            }
        });
    }

    public Task createSubtask(int parentId, TaskRequest taskRequest) {
        var parent = getTask(parentId);

        if (parent.getParentId() > 0) {
            throw new ValidationException("parentId", "A subtask cannot be given subtasks");
        }

        return createSubtaskInternal(parentId, taskRequest);
    }

    /** Backwards-compatible overload that takes the parent id from the request. */
    public Task createSubtask(TaskRequest taskRequest) {
        if (taskRequest == null || taskRequest.getParentTaskId() == null
                || taskRequest.getParentTaskId().isEmpty()) {
            throw new ValidationException("parentTaskId", "parentTaskId is required");
        }

        return createSubtask(taskRequest.getParentTaskId().get(), taskRequest);
    }

    private Task createSubtaskInternal(int parentId, TaskRequest request) {
        validateTaskRequest(request);

        var task = new TaskBuilder(request.getTitle(), request.getDescription(), false)
                .setId(IdGenerator.getNextTaskId())
                .setPriority(Enum.valueOf(Priority.class, request.getPriority()))
                .setDueDate(request.getDueDate())
                .setParentId(parentId)
                .build();

        return taskRepository.upsert(task);
    }

    public List<Task> listSubtasks(int parentId) {

        return listTasks(null).stream().filter(x -> x.getParentId() == parentId).toList();

    }

    public List<Task> breakDownTask(int id, String idempotencyKey) {

        var fingerprint = IdempotencyStore.fingerprint("breakDownTask", id);

        return idempotencyStore.execute(idempotencyKey, fingerprint, () -> breakDownTaskInternal(id));
    }

    private List<Task> breakDownTaskInternal(int id) {
        var task = getTask(id);

        if (task.getParentId() > 0) {
            throw new ValidationException("id", "A subtask cannot be broken down into subtasks");
        }

        var subtaskRequests = breakdownStrategy.breakDown(task);

        if (subtaskRequests == null) {
            throw new BadGatewayException("Breakdown returned no requests");
        }

        // Validate every request before creating anything.
        var issues = new ArrayList<FieldIssue>();

        for (TaskRequest subtaskRequest : subtaskRequests) {
            if (subtaskRequest == null) {
                issues.add(new FieldIssue("subtasks", "Breakdown returned a null request"));
                continue;
            }

            subtaskRequest.setParentTaskId(Optional.of(task.getId()));

            try {
                validateTaskRequest(subtaskRequest);
            } catch (ValidationException exception) {
                issues.addAll(exception.getIssues());
            }
        }

        if (!issues.isEmpty()) {
            throw new BadGatewayException("Breakdown returned invalid subtasks", issues);
        }

        var subtasks = new ArrayList<Task>();

        for (TaskRequest subtaskRequest : subtaskRequests) {
            subtasks.add(createSubtaskInternal(task.getId(), subtaskRequest));
        }

        return List.copyOf(subtasks);
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
