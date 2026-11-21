package com.airtribe.taskmanager.model;

public record ErrorDto(String code, String message, Object details) {
}