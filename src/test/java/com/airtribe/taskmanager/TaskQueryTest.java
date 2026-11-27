package com.airtribe.taskmanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.airtribe.taskmanager.api.TaskApi;
import com.airtribe.taskmanager.model.ApiResponse;
import com.airtribe.taskmanager.model.ErrorDto;
import com.airtribe.taskmanager.model.Priority;
import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskQuery;
import com.airtribe.taskmanager.repository.InMemoryTaskRepository;
import com.airtribe.taskmanager.service.TaskService;

public class TaskQueryTest {

    private InMemoryTaskRepository repository;
    private TaskService taskService;
    private TaskApi taskApi;

    @Before
    public void setUp() {
        repository = new InMemoryTaskRepository();
        repository.findAll().clear();

        taskService = new TaskService(repository, null);
        taskApi = new TaskApi(taskService, repository, null);
    }

    @Test
    public void builderHasOptionalFieldsAndPaginationDefaults() {
        var query = TaskQuery.builder().build();

        assertNull(query.getCompleted());
        assertNull(query.getPriority());
        assertNull(query.getSearch());
        assertNull(query.getSortBy());
        assertNull(query.getOrder());
        assertEquals(1, query.getPage());
        assertEquals(10, query.getPageSize());
    }

    @Test
    public void completedFilterReturnsOnlyMatchingTasks() {
        addTask(1, "Open task", "description", false, "2026-12-01", Priority.HIGH);
        addTask(2, "Done task", "description", true, "2026-12-02", Priority.HIGH);
        addTask(3, "Another done task", "description", true, "2026-12-03", Priority.HIGH);

        var completed = taskService.listTasks(TaskQuery.builder().completed(true).build());
        assertEquals(List.of(2, 3), ids(completed));

        var open = taskService.listTasks(TaskQuery.builder().completed(false).build());
        assertEquals(List.of(1), ids(open));
    }

    @Test
    public void priorityFilterIsCaseInsensitive() {
        addTask(1, "High", "description", false, "2026-12-01", Priority.HIGH);
        addTask(2, "Low", "description", false, "2026-12-02", Priority.LOW);
        addTask(3, "Medium", "description", false, "2026-12-03", Priority.MEDIUM);

        var result = taskService.listTasks(TaskQuery.builder().priority("high").build());

        assertEquals(List.of(1), ids(result));
    }

    @Test
    public void invalidPriorityReturnsBadRequest() {
        ApiResponse response = taskApi.listTasks(TaskQuery.builder().priority("URGENT").build());

        assertEquals(400, response.getStatus());
        assertEquals("VALIDATION_ERROR", ((ErrorDto) response.getBody()).code());
    }

    @Test
    public void searchMatchesTitleOrDescriptionIgnoringCase() {
        addTask(1, "Alpha report", "quarterly numbers", false, "2026-12-01", Priority.HIGH);
        addTask(2, "Beta", "contains the Needle here", false, "2026-12-02", Priority.HIGH);

        assertEquals(List.of(2), ids(taskService.listTasks(TaskQuery.builder().search("NEEDLE").build())));
        assertEquals(List.of(1), ids(taskService.listTasks(TaskQuery.builder().search("alpha").build())));
    }

    @Test
    public void sortsByDueDateInBothDirections() {
        addTask(1, "Third", "description", false, "2026-12-03", Priority.HIGH);
        addTask(2, "First", "description", false, "2026-12-01", Priority.HIGH);
        addTask(3, "Second", "description", false, "2026-12-02", Priority.HIGH);

        var ascending = taskService.listTasks(
                TaskQuery.builder().sortBy("dueDate").order("asc").build());
        var descending = taskService.listTasks(
                TaskQuery.builder().sortBy("dueDate").order("desc").build());

        assertEquals(List.of(2, 3, 1), ids(ascending));
        assertEquals(List.of(1, 3, 2), ids(descending));
    }

    @Test
    public void sortsByCreatedInBothDirections() throws InterruptedException {
        addTask(1, "Oldest", "description", false, "2026-12-03", Priority.HIGH);
        Thread.sleep(5);
        addTask(2, "Middle", "description", false, "2026-12-02", Priority.HIGH);
        Thread.sleep(5);
        addTask(3, "Newest", "description", false, "2026-12-01", Priority.HIGH);

        var ascending = taskService.listTasks(
                TaskQuery.builder().sortBy("created").order("asc").build());
        var descending = taskService.listTasks(
                TaskQuery.builder().sortBy("created").order("desc").build());

        assertEquals(List.of(1, 2, 3), ids(ascending));
        assertEquals(List.of(3, 2, 1), ids(descending));
    }

    @Test
    public void paginatesWithDefaultPageSize() {
        for (int i = 1; i <= 15; i++) {
            addTask(i, "Task " + i, "description", false, "2026-12-01", Priority.HIGH);
        }

        var firstPage = taskService.listTasks(TaskQuery.builder().build());
        var secondPage = taskService.listTasks(TaskQuery.builder().page(2).build());

        assertEquals(10, firstPage.size());
        assertEquals(5, secondPage.size());
        assertEquals(List.of(11, 12, 13, 14, 15), ids(secondPage));
    }

    @Test
    public void filteringSearchSortingAndPaginationWorkTogether() {
        for (int i = 1; i <= 15; i++) {
            Priority priority = i % 2 == 0 ? Priority.HIGH : Priority.LOW;
            addTask(i, "Milk task " + i, "weekly groceries", false,
                    String.format("2026-12-%02d", i), priority);
        }

        var pageOne = taskService.listTasks(TaskQuery.builder()
                .completed(false)
                .priority("HIGH")
                .search("milk")
                .sortBy("dueDate")
                .order("desc")
                .page(1)
                .pageSize(3)
                .build());

        var pageTwo = taskService.listTasks(TaskQuery.builder()
                .completed(false)
                .priority("HIGH")
                .search("milk")
                .sortBy("dueDate")
                .order("desc")
                .page(2)
                .pageSize(3)
                .build());

        assertEquals(List.of(14, 12, 10), ids(pageOne));
        assertEquals(List.of(8, 6, 4), ids(pageTwo));
    }

    @Test
    public void invalidSortingAndPaginationReturnBadRequest() {
        assertEquals(400, taskApi.listTasks(TaskQuery.builder().sortBy("title").build()).getStatus());
        assertEquals(400, taskApi.listTasks(TaskQuery.builder().sortBy("created").order("up").build()).getStatus());
        assertEquals(400, taskApi.listTasks(TaskQuery.builder().page(0).build()).getStatus());
        assertEquals(400, taskApi.listTasks(TaskQuery.builder().pageSize(0).build()).getStatus());
    }

    @Test
    public void nullQueryKeepsReturningAllTasks() {
        for (int i = 1; i <= 15; i++) {
            addTask(i, "Task " + i, "description", false, "2026-12-01", Priority.HIGH);
        }

        assertTrue(taskService.listTasks(null).size() == 15);
    }

    private Task addTask(int id, String title, String description, boolean completed,
            String dueDate, Priority priority) {
        var task = new Task.TaskBuilder(title, description, completed)
                .setId(id)
                .setDueDate(dueDate)
                .setPriority(priority)
                .build();

        return repository.upsert(task);
    }

    private static List<Integer> ids(List<Task> tasks) {
        return tasks.stream().map(Task::getId).toList();
    }
}
