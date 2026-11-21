package com.airtribe.taskmanager.repository;

import java.util.List;
import java.util.Optional;

import com.airtribe.taskmanager.model.Task;

public interface TaskRepository {
    // save, findById, findAll, and delete

    Task upsert(Task task);

    Optional<Task> findById(int id);

    List<Task> findAll();

    boolean delete(int id);

}
