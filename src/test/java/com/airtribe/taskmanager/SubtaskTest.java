package com.airtribe.taskmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import com.airtribe.taskmanager.api.TaskApi;
import com.airtribe.taskmanager.exception.BadGatewayException;
import com.airtribe.taskmanager.exception.ValidationException;
import com.airtribe.taskmanager.model.ApiResponse;
import com.airtribe.taskmanager.model.ErrorDto;
import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskRequest;
import com.airtribe.taskmanager.parser.LlmClient;
import com.airtribe.taskmanager.repository.InMemoryTaskRepository;
import com.airtribe.taskmanager.service.IdempotencyStore;
import com.airtribe.taskmanager.service.TaskQueryEngine;
import com.airtribe.taskmanager.service.TaskService;
import com.airtribe.taskmanager.service.breakdown.BreakdownStrategy;
import com.airtribe.taskmanager.service.breakdown.LlmBreakdownStrategy;

public class SubtaskTest {

    private InMemoryTaskRepository repository;
    private TaskService taskService;
    private TaskApi taskApi;

    @Before
    public void setUp() {
        IdempotencyStore.getInstance().clear();

        repository = new InMemoryTaskRepository();
        repository.findAll().clear();

        // A null parser selects the offline RuleBasedBreakdownStrategy.
        taskService = new TaskService(repository, null);
        taskApi = new TaskApi(taskService, repository, null);
    }

    @Test
    public void createSubtaskSetsParentAndReturns201() {
        var parent = taskService.createTask(request("Parent task", "Parent description"), null);

        ApiResponse response = taskApi.createSubtask(parent.getId(), request("Child task", "Child description"));

        assertEquals(201, response.getStatus());
        var child = (Task) response.getBody();
        assertEquals(parent.getId(), child.getParentId());
        assertEquals(List.of(child.getId()), ids(taskService.listSubtasks(parent.getId())));
    }

    @Test
    public void cannotAddSubtaskToASubtask() {
        var parent = taskService.createTask(request("Parent task", "Parent description"), null);
        var child = taskService.createSubtask(parent.getId(), request("Child task", "Child description"));

        ApiResponse response = taskApi.createSubtask(child.getId(), request("Grandchild", "Grandchild description"));

        assertEquals(400, response.getStatus());
        assertEquals("VALIDATION_ERROR", ((ErrorDto) response.getBody()).code());
        assertEquals(2, repository.findAll().size());
    }

    @Test
    public void createSubtaskForMissingParentReturns404() {
        ApiResponse response = taskApi.createSubtask(999_999, request("Child", "Description"));

        assertEquals(404, response.getStatus());
        assertEquals("NOT_FOUND", ((ErrorDto) response.getBody()).code());
    }

    @Test
    public void invalidSubtaskRequestReturns400() {
        var parent = taskService.createTask(request("Parent task", "Parent description"), null);
        var invalid = request("", "Description");

        ApiResponse response = taskApi.createSubtask(parent.getId(), invalid);

        assertEquals(400, response.getStatus());
        assertEquals("VALIDATION_ERROR", ((ErrorDto) response.getBody()).code());
    }

    @Test
    public void ruleBasedBreakdownCreatesSubtasks() {
        var parent = taskService.createTask(request("Buy milk and bread", "Groceries"), null);

        var subtasks = taskService.breakDownTask(parent.getId(), "rule-breakdown-key");

        assertEquals(2, subtasks.size());
        assertEquals(List.of("Buy milk", "bread"), subtasks.stream().map(Task::getTitle).toList());
        assertTrue(subtasks.stream().allMatch(task -> task.getParentId() == parent.getId()));
        assertEquals(3, repository.findAll().size());
    }

    @Test
    public void ruleBasedBreakdownSplitsOnCommas() {
        var parent = taskService.createTask(request("Buy milk, bread, eggs", "Groceries"), null);

        var subtasks = taskService.breakDownTask(parent.getId(), "comma-breakdown-key");

        assertEquals(List.of("Buy milk", "bread", "eggs"),
                subtasks.stream().map(Task::getTitle).toList());
    }

    @Test
    public void breakdownOfASubtaskReturns400() {
        var parent = taskService.createTask(request("Parent task", "Parent description"), null);
        var child = taskService.createSubtask(parent.getId(), request("Child task", "Child description"));

        ApiResponse response = taskApi.breakDownTask(child.getId(), "subtask-breakdown-key");

        assertEquals(400, response.getStatus());
        assertEquals("VALIDATION_ERROR", ((ErrorDto) response.getBody()).code());
    }

    @Test
    public void llmBreakdownParsesTitleDescriptionLines() {
        var service = serviceWithStrategy(new LlmBreakdownStrategy(
                new FakeLlmClient("Buy milk|2L cow milk\nBuy bread|Whole wheat bread")));
        var parent = service.createTask(request("Groceries", "Weekly shopping"), null);

        var subtasks = service.breakDownTask(parent.getId(), "llm-key");

        assertEquals(2, subtasks.size());
        assertEquals(List.of("Buy milk", "Buy bread"), subtasks.stream().map(Task::getTitle).toList());
        assertEquals(List.of("2L cow milk", "Whole wheat bread"),
                subtasks.stream().map(Task::getDescription).toList());
        assertTrue(subtasks.stream().allMatch(task -> task.getParentId() == parent.getId()));
    }

    @Test
    public void llmBreakdownToleratesCodeFencesAndBullets() {
        var service = serviceWithStrategy(new LlmBreakdownStrategy(
                new FakeLlmClient("```\n- Buy milk|2L cow milk\n* Buy bread|Whole wheat bread\n```")));
        var parent = service.createTask(request("Groceries", "Weekly shopping"), null);

        var subtasks = service.breakDownTask(parent.getId(), "llm-lenient-key");

        assertEquals(List.of("Buy milk", "Buy bread"), subtasks.stream().map(Task::getTitle).toList());
    }

    @Test
    public void invalidLlmItemSavesNoneAndReturns502() {
        var service = serviceWithStrategy(new LlmBreakdownStrategy(
                new FakeLlmClient("Buy milk|2L cow milk\nThis line has no separator")));
        var api = new TaskApi(service, repository, null);
        var parent = service.createTask(request("Groceries", "Weekly shopping"), null);

        ApiResponse response = api.breakDownTask(parent.getId(), "invalid-llm-key");

        assertEquals(502, response.getStatus());
        assertEquals("BAD_GATEWAY", ((ErrorDto) response.getBody()).code());
        assertEquals(1, repository.findAll().size());
        assertEquals(0, IdempotencyStore.getInstance().size());
    }

    @Test
    public void llmCallFailureSavesNoneAndReturns502() {
        var service = serviceWithStrategy(new LlmBreakdownStrategy(new FakeLlmClient(null, true)));
        var api = new TaskApi(service, repository, null);
        var parent = service.createTask(request("Groceries", "Weekly shopping"), null);

        ApiResponse response = api.breakDownTask(parent.getId(), "failed-llm-key");

        assertEquals(502, response.getStatus());
        assertEquals("BAD_GATEWAY", ((ErrorDto) response.getBody()).code());
        assertEquals(1, repository.findAll().size());
        assertEquals(0, IdempotencyStore.getInstance().size());
    }

    @Test
    public void serviceValidatesEveryBreakdownItemBeforeSavingAny() {
        BreakdownStrategy strategy = task -> List.of(request("Valid title", "Valid description"),
                request("Invalid title", ""));

        var service = serviceWithStrategy(strategy);
        var api = new TaskApi(service, repository, null);
        var parent = service.createTask(request("Parent", "Parent description"), null);

        ApiResponse response = api.breakDownTask(parent.getId(), "validation-key");

        assertEquals(502, response.getStatus());
        assertEquals("BAD_GATEWAY", ((ErrorDto) response.getBody()).code());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    public void updatingSubtaskKeepsParentAndCreatedAt() {
        var parent = taskService.createTask(request("Parent", "Parent description"), null);
        var child = taskService.createSubtask(parent.getId(), request("Child", "Child description"));
        long originalCreatedAt = child.getCreatedAt();

        var update = request("Child renamed", "Updated description");
        update.setCompleted(true);
        var updated = taskService.updateTask(child.getId(), update);

        assertEquals(parent.getId(), updated.getParentId());
        assertEquals(originalCreatedAt, updated.getCreatedAt());
        assertEquals(List.of(updated.getId()), ids(taskService.listSubtasks(parent.getId())));
    }

    @Test
    public void deletingParentDeletesItsSubtasks() {
        var parent = taskService.createTask(request("Buy milk and bread", "Groceries"), null);
        taskService.breakDownTask(parent.getId(), "delete-key");

        System.out.println("Repository BEFORE delete: " + ids(repository.findAll()));
        assertEquals(3, repository.findAll().size());

        boolean deleted = taskService.deleteTask(parent.getId());
        System.out.println("Repository AFTER delete : " + ids(repository.findAll()));

        assertTrue(deleted);
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    public void serviceCannotCreateSubtaskUnderASubtask() {
        var parent = taskService.createTask(request("Parent", "Parent description"), null);
        var child = taskService.createSubtask(parent.getId(), request("Child", "Child description"));

        assertThrows(ValidationException.class,
                () -> taskService.createSubtask(child.getId(), request("Grandchild", "Description")));
        assertEquals(2, repository.findAll().size());
    }

    private TaskService serviceWithStrategy(BreakdownStrategy strategy) {
        return new TaskService(repository, null, new TaskQueryEngine(),
                IdempotencyStore.getInstance(), strategy);
    }

    private static TaskRequest request(String title, String description) {
        var request = new TaskRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setPriority("HIGH");
        request.setDueDate("2026-12-01");
        request.setParentTaskId(Optional.empty());
        return request;
    }

    private static List<Integer> ids(List<Task> tasks) {
        return tasks.stream().map(Task::getId).toList();
    }

    private static <T extends Throwable> T assertThrows(Class<T> expectedType, Runnable action) {
        try {
            action.run();
            fail("Expected " + expectedType.getSimpleName() + " to be thrown");
            return null;
        } catch (Throwable throwable) {
            if (expectedType.isInstance(throwable)) {
                return expectedType.cast(throwable);
            }
            throw throwable;
        }
    }

    private static final class FakeLlmClient implements LlmClient {

        private final String reply;
        private final boolean fail;

        FakeLlmClient(String reply) {
            this(reply, false);
        }

        FakeLlmClient(String reply, boolean fail) {
            this.reply = reply;
            this.fail = fail;
        }

        @Override
        public String send(String prompt) throws IOException {
            if (fail) {
                throw new IOException("network down");
            }
            return reply;
        }
    }
}
