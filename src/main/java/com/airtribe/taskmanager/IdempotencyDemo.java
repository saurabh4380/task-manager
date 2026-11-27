package com.airtribe.taskmanager;

import com.airtribe.taskmanager.exception.ConflictException;
import com.airtribe.taskmanager.model.TaskRequest;
import com.airtribe.taskmanager.repository.InMemoryTaskRepository;
import com.airtribe.taskmanager.service.IdempotencyStore;
import com.airtribe.taskmanager.service.TaskService;

/**
 * Runnable proof that retrying the same create request with the same key does
 * not produce a duplicate task.
 *
 * <pre>
 * mvn clean package -DskipTests
 * java -cp target/task-manager-1.0-SNAPSHOT.jar com.airtribe.taskmanager.IdempotencyDemo
 * </pre>
 */
public class IdempotencyDemo {

    public static void main(String[] args) {
        IdempotencyStore.getInstance().clear();

        var repository = new InMemoryTaskRepository();
        repository.findAll().clear();

        var taskService = new TaskService(repository, null);

        var request = new TaskRequest();
        request.setTitle("Buy milk");
        request.setDescription("2L cow milk");
        request.setPriority("HIGH");
        request.setDueDate("2026-12-01");

        var key = "demo-idempotency-key";

        var first = taskService.createTask(request, key);
        var retry = taskService.createTask(request, key);

        System.out.println("First task id : " + first.getId());
        System.out.println("Retry task id : " + retry.getId());

        var differentRequest = new TaskRequest();
        differentRequest.setTitle("Buy bread");
        differentRequest.setDescription("Whole wheat bread");
        differentRequest.setPriority("HIGH");
        differentRequest.setDueDate("2026-12-02");

        try {
            taskService.createTask(differentRequest, key);
        } catch (ConflictException exception) {
            System.out.println("Same key, different request -> "
                    + exception.getStatus() + " " + exception.getCode());
        }

        System.out.println("Repository size: " + repository.findAll().size());
        System.out.println("Repository contents:");
        repository.findAll().forEach(task -> System.out.println("  #" + task.getId() + " " + task.getTitle()));

        if (repository.findAll().size() == 1) {
            System.out.println("PASS: only one task was created.");
        } else {
            System.out.println("FAIL: duplicate task was created.");
        }
    }
}
