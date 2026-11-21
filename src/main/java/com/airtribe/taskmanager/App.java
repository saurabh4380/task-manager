package com.airtribe.taskmanager;

import com.airtribe.taskmanager.api.TaskApi;
import com.airtribe.taskmanager.model.TaskQuery;
import com.airtribe.taskmanager.model.TaskRequest;
import com.airtribe.taskmanager.repository.InMemoryTaskRepository;
import com.airtribe.taskmanager.service.TaskService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public class App {

    public static void main(String args[]) {

        var taskRepository = new InMemoryTaskRepository();

        var taskService = new TaskService(taskRepository);

        var taskApi = new TaskApi(taskService, taskRepository);

        try {
            var taskRequest = new TaskRequest();
            taskRequest.setTitle("Buy milk");
            taskRequest.setDescription("2L cow milk");
            taskRequest.setPriority("HIGH");
            taskRequest.setDueDate("2026-11-21");
            var response = taskApi.createTask(taskRequest, "");

            JsonMapper mapper = JsonMapper.builder().build();

            String json = mapper.writeValueAsString(response);
            System.out.println("Created Task --> " + json);

            var getTaskApiResponse = taskApi.getTask(1);
            System.out.println("Get Task --> " + mapper.writeValueAsString(getTaskApiResponse.getBody()));

            taskRequest.setDescription("1L Cow Milk + 1L Buffalo milk");
            var updateTaskApiResponse = taskApi.updateTask(1, taskRequest);

            System.out.println("Update Task --> " + mapper.writeValueAsString(updateTaskApiResponse.getBody()));

            var listTasksApiResponse = taskApi.listTasks(new TaskQuery());

            System.out.println("List Tasks --> " + mapper.writeValueAsString(listTasksApiResponse.getBody()));

            var deleteTaskApiResponse = taskApi.deleteTask(1);

            if (deleteTaskApiResponse.getStatus() != 200) {
                System.err.println("Failed to delete task with Id: " + 1);
            }

            listTasksApiResponse = taskApi.listTasks(new TaskQuery());

            System.out.println(
                    "List Tasks (After task Removal) --> " + mapper.writeValueAsString(listTasksApiResponse.getBody()));

        } catch (JacksonException jacksonException) {
            System.err.println(jacksonException.toString());
        }

    }

}
