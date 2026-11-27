package com.airtribe.taskmanager;

import com.airtribe.taskmanager.model.Task;
import com.airtribe.taskmanager.model.TaskRequest;
import com.airtribe.taskmanager.repository.InMemoryTaskRepository;
import com.airtribe.taskmanager.service.IdempotencyStore;
import com.airtribe.taskmanager.service.TaskService;

import java.util.List;

/**
 * Runnable proof that a parent breakdown creates subtasks and that deleting the
 * parent deletes those subtasks.
 *
 * <pre>
 * mvn clean package -DskipTests
 * java -cp target/task-manager-1.0-SNAPSHOT.jar com.airtribe.taskmanager.SubtaskDemo
 * </pre>
 */
public class SubtaskDemo {

    public static void main(String[] args) {
        IdempotencyStore.getInstance().clear();

        var repository = new InMemoryTaskRepository();
        repository.findAll().clear();

        // No LLM parser -> RuleBasedBreakdownStrategy (split on "and" / commas).
        var taskService = new TaskService(repository, null);

        var request = new TaskRequest();
        request.setTitle("Buy milk and bread");
        request.setDescription("Groceries for the week");
        request.setPriority("HIGH");
        request.setDueDate("2026-12-01");

        var parent = taskService.createTask(request, null);
        var subtasks = taskService.breakDownTask(parent.getId(), "demo-breakdown-key");

        System.out.println("Parent: #" + parent.getId() + " " + parent.getTitle());
        System.out.println("Created subtasks: " + subtasks.size());

        printRepository(repository, "Repository BEFORE deleting the parent");

        taskService.deleteTask(parent.getId());

        printRepository(repository, "Repository AFTER deleting the parent");

        if (repository.findAll().isEmpty()) {
            System.out.println("PASS: deleting the parent also deleted its subtasks.");
        } else {
            System.out.println("FAIL: subtasks were left behind.");
        }
    }

    private static void printRepository(InMemoryTaskRepository repository, String heading) {
        List<Task> tasks = repository.findAll();

        System.out.println(heading + " (" + tasks.size() + " task(s)):");
        for (Task task : tasks) {
            System.out.println("  #" + task.getId()
                    + " parentId=" + task.getParentId()
                    + " " + task.getTitle());
        }
    }
}
