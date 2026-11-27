package com.airtribe.taskmanager;

import com.airtribe.taskmanager.api.TaskApi;
import com.airtribe.taskmanager.model.TaskQuery;
import com.airtribe.taskmanager.model.TaskRequest;
import com.airtribe.taskmanager.parser.HttpLlmClient;
import com.airtribe.taskmanager.parser.LlmTaskParse;
import com.airtribe.taskmanager.repository.InMemoryTaskRepository;
import com.airtribe.taskmanager.service.TaskService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public class App {

    public static void main(String args[]) {

        var taskRepository = new InMemoryTaskRepository();

        var llmApiKey = System.getenv("LLM_API_KEY");
        var llmClient = new HttpLlmClient(llmApiKey, "deepseek", "deepseek-flash");
        var taskParser = new LlmTaskParse(llmClient);
        var taskService = new TaskService(taskRepository, taskParser);

        var taskApi = new TaskApi(taskService, taskRepository, taskParser);

        try {
            JsonMapper mapper = JsonMapper.builder().build();

            var taskRequest = new TaskRequest();
            taskRequest.setTitle("Buy milk");
            taskRequest.setDescription("2L cow milk");
            taskRequest.setPriority("HIGH");
            taskRequest.setDueDate("2026-11-21");

            var createTaskFromTextApiResponse = taskApi.createTaskFromText("Buy milk from Guruprasad General Store",
                    null);

            System.out.println(
                    "Create Task from Text --> " + mapper.writeValueAsString(createTaskFromTextApiResponse.getBody()));

            var response = taskApi.createTask(taskRequest, "");

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
