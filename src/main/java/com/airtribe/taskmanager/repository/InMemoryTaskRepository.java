package com.airtribe.taskmanager.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import com.airtribe.taskmanager.model.Task;

//TODO: check if we can use this entire class as a singelton
public class InMemoryTaskRepository implements TaskRepository {

    private static List<Task> tasks = Collections.synchronizedList(new ArrayList<>());

    @Override
    public Task upsert(Task task) {
        var idx = IntStream.range(0, tasks.size())
                .filter(i -> tasks.get(i).getId() == task.getId())
                .findFirst()
                .orElse(-1);

        if (idx > -1) {
            tasks.set(idx, task);
        } else {
            tasks.add(task);
        }

        return task;
    }

    @Override
    public Optional<Task> findById(int id) {
        return tasks.stream().filter(x -> x.getId() == id).findFirst();
    }

    @Override
    public List<Task> findAll() {
        return tasks;
    }

    @Override
    public boolean delete(int id) {
        return tasks.removeIf(x -> x.getId() == id);
    }

}
