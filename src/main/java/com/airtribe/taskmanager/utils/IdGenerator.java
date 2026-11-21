package com.airtribe.taskmanager.utils;

import java.util.concurrent.atomic.AtomicInteger;

public class IdGenerator {
    private static final AtomicInteger taskCounter = new AtomicInteger(1);

    public static int getNextTaskId() {
        return taskCounter.getAndIncrement();
    }
}
