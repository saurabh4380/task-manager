package com.airtribe.taskmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import com.airtribe.taskmanager.api.TaskApi;
import com.airtribe.taskmanager.exception.ConflictException;
import com.airtribe.taskmanager.exception.ValidationException;
import com.airtribe.taskmanager.model.ApiResponse;
import com.airtribe.taskmanager.model.ErrorDto;
import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskRequest;
import com.airtribe.taskmanager.parser.ParsedTask;
import com.airtribe.taskmanager.parser.TaskParser;
import com.airtribe.taskmanager.repository.InMemoryTaskRepository;
import com.airtribe.taskmanager.service.IdempotencyStore;
import com.airtribe.taskmanager.service.TaskService;

public class IdempotencyTest {

    private InMemoryTaskRepository repository;
    private CountingParser parser;
    private TaskService taskService;
    private TaskApi taskApi;

    @Before
    public void setUp() {
        IdempotencyStore.getInstance().clear();

        repository = new InMemoryTaskRepository();
        repository.findAll().clear();

        parser = new CountingParser();
        taskService = new TaskService(repository, parser);
        taskApi = new TaskApi(taskService, repository, parser);
    }

    @Test
    public void singletonReturnsTheSameStore() {
        assertSame(IdempotencyStore.getInstance(), IdempotencyStore.getInstance());
    }

    @Test
    public void sameKeyAndSameRequestReturnsStoredResponseAndCreatesOneTask() {
        var request = request("Buy milk");
        var key = "same-request-key";

        var first = taskService.createTask(request, key);
        var retry = taskService.createTask(request, key);

        assertEquals(first.getId(), retry.getId());
        assertEquals(1, repository.findAll().size());

        System.out.println("Repository after idempotent retry: "
                + repository.findAll().size() + " task(s)");
    }

    @Test
    public void sameKeyWithDifferentRequestReturns422AndCreatesNothing() {
        taskService.createTask(request("Buy milk"), "conflict-key");

        ApiResponse response = taskApi.createTask(request("Buy bread"), "conflict-key");

        assertEquals(422, response.getStatus());
        assertEquals("CONFLICT", ((ErrorDto) response.getBody()).code());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    public void failedRequestIsNotStoredAndCanBeRetriedWithTheSameKey() {
        var request = request("Retry me");
        request.setPriority("NOT_A_PRIORITY");
        var key = "fix-and-retry-key";

        assertThrows(ValidationException.class, () -> taskService.createTask(request, key));
        assertEquals(0, IdempotencyStore.getInstance().size());

        request.setPriority("HIGH");
        var task = taskService.createTask(request, key);

        assertNotNull(task);
        assertEquals(1, repository.findAll().size());
    }

    @Test
    public void createTaskFromTextDoesNotCallParserAgainOnRetry() {
        var first = taskService.createTaskFromText("buy milk tomorrow", "text-key");
        var retry = taskService.createTaskFromText("buy milk tomorrow", "text-key");

        assertEquals(1, parser.calls);
        assertEquals(first.getId(), retry.getId());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    public void createTaskFromTextWithDifferentTextReturns422WithoutParsingAgain() {
        taskService.createTaskFromText("buy milk tomorrow", "text-conflict-key");

        assertThrows(ConflictException.class,
                () -> taskService.createTaskFromText("buy bread tomorrow", "text-conflict-key"));

        assertEquals(1, parser.calls);
        assertEquals(1, repository.findAll().size());
    }

    @Test
    public void breakDownTaskUsesIdempotency() {
        var parent = taskService.createTask(request("Parent task"), null);
        var key = "breakdown-key";

        var first = taskService.breakDownTask(parent.getId(), key);
        var retry = taskService.breakDownTask(parent.getId(), key);

        assertSame(first, retry);
        assertEquals(1, first.size());
        assertEquals(parent.getId(), first.get(0).getParentId());
        assertEquals(2, repository.findAll().size());

        assertThrows(ConflictException.class,
                () -> taskService.breakDownTask(parent.getId() + 999, key));
    }

    @Test
    public void nullOrBlankKeyDisablesIdempotency() {
        taskService.createTask(request("No key"), null);
        taskService.createTask(request("No key"), null);
        taskService.createTask(request("No key"), "   ");

        assertEquals(3, repository.findAll().size());
    }

    @Test
    public void concurrentRetriesWithTheSameKeyCreateOneTask() throws Exception {
        var request = request("Concurrent task");
        var key = "concurrent-key";
        var workers = 8;

        var pool = Executors.newFixedThreadPool(workers);
        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<Task>>();

        for (int i = 0; i < workers; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return taskService.createTask(request, key);
            }));
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();

        var ids = new HashSet<Integer>();
        for (Future<Task> future : futures) {
            ids.add(future.get(5, TimeUnit.SECONDS).getId());
        }

        pool.shutdownNow();

        assertEquals(1, ids.size());
        assertEquals(1, repository.findAll().size());
    }

    private static TaskRequest request(String title) {
        var request = new TaskRequest();
        request.setTitle(title);
        request.setDescription("Description for " + title);
        request.setPriority("HIGH");
        request.setDueDate("2026-12-01");
        request.setParentTaskId(Optional.empty());
        return request;
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

    private static final class CountingParser implements TaskParser {

        private int calls;

        @Override
        public ParsedTask parse(String text, String today) {
            calls++;
            return new ParsedTask("Parsed: " + text, "Parsed description", "2026-12-01", null);
        }
    }
}
