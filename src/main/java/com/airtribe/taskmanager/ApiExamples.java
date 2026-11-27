package com.airtribe.taskmanager;

import com.airtribe.taskmanager.api.TaskApi;
import com.airtribe.taskmanager.model.ApiResponse;
import com.airtribe.taskmanager.model.TaskQuery;
import com.airtribe.taskmanager.model.TaskRequest;
import com.airtribe.taskmanager.parser.LlmClient;
import com.airtribe.taskmanager.parser.LlmTaskParse;
import com.airtribe.taskmanager.repository.InMemoryTaskRepository;
import com.airtribe.taskmanager.service.IdempotencyStore;
import com.airtribe.taskmanager.service.TaskService;

import tools.jackson.databind.json.JsonMapper;

/**
 * Prints one example call and response for every TaskApi operation.
 *
 * <p>Uses a fake LLM client so the output is reproducible without a key:
 *
 * <pre>
 * mvn clean package -DskipTests
 * java -cp target/task-manager-1.0-SNAPSHOT.jar com.airtribe.taskmanager.ApiExamples
 * </pre>
 */
public class ApiExamples {

    public static void main(String[] args) throws Exception {
        IdempotencyStore.getInstance().clear();

        var repository = new InMemoryTaskRepository();
        repository.findAll().clear();

        var parser = new LlmTaskParse(new FakeLlmClient());
        var taskService = new TaskService(repository, parser);
        var api = new TaskApi(taskService, repository, parser);
        var mapper = JsonMapper.builder().build();

        print(mapper, "createTask",
                api.createTask(request("Buy milk", "2L cow milk", "HIGH", "2026-12-03"), "create-1"));

        print(mapper, "getTask",
                api.getTask(1));

        print(mapper, "createSubtask",
                api.createSubtask(1, request("Buy bread", "Whole wheat bread", "LOW", "2026-12-01")));

        print(mapper, "listSubtasks",
                api.listSubtasks(1));

        api.createTask(request("Pay rent", "December rent", "HIGH", "2026-12-02"), null);

        var query = TaskQuery.builder()
                .search("buy")
                .sortBy("dueDate")
                .order("asc")
                .page(1)
                .pageSize(10)
                .build();
        print(mapper, "listTasks (search=buy, sort=dueDate asc)",
                api.listTasks(query));

        var update = request("Buy milk", "1L cow milk + 1L buffalo milk", "HIGH", "2026-12-03");
        update.setCompleted(true);
        print(mapper, "updateTask",
                api.updateTask(1, update));

        print(mapper, "createTaskFromText (AI)",
                api.createTaskFromText("Buy milk from the corner store", "ai-create-1"));

        print(mapper, "breakDownTask (AI)",
                api.breakDownTask(1, "breakdown-1"));

        print(mapper, "deleteTask",
                api.deleteTask(1));

        print(mapper, "getTask after delete",
                api.getTask(1));

        var invalid = request(null, "Description", "HIGH", "2026-12-01");
        print(mapper, "validation error",
                api.createTask(invalid, null));

        print(mapper, "idempotency: first request",
                api.createTask(request("Idempotent task", "Original", "HIGH", "2026-12-01"), "same-key"));

        print(mapper, "idempotency conflict (different request, same key)",
                api.createTask(request("Different task", "Changed", "HIGH", "2026-12-02"), "same-key"));
    }

    private static void print(JsonMapper mapper, String label, ApiResponse response) throws Exception {
        System.out.println(label + " -> " + mapper.writeValueAsString(response));
    }

    private static TaskRequest request(String title, String description, String priority, String dueDate) {
        var request = new TaskRequest();
        request.setTitle(title);
        request.setDescription(description);
        request.setPriority(priority);
        request.setDueDate(dueDate);
        return request;
    }

    private static final class FakeLlmClient implements LlmClient {

        @Override
        public String send(String prompt) {
            return "{\"title\":\"Buy milk\",\"description\":\"2L cow milk\","
                    + "\"dueDate\":\"2026-12-01\",\"recurrence\":null}";
        }

        @Override
        public String sendRaw(String prompt) {
            return "Buy milk|2L cow milk\nBuy bread|Whole wheat bread";
        }
    }
}
