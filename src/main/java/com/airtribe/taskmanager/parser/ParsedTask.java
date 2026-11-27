package com.airtribe.taskmanager.parser;

public record ParsedTask(String title, String description, String dueDate, String recurrence) {
}