package com.airtribe.taskmanager.utils;

import com.airtribe.taskmanager.service.DateHelper;

public class Validator {

    public static boolean isNullOrEmpty(String data) {
        return data == null || data.isBlank();
    }

    public static boolean isNotNullOrEmpty(String data) {
        return !isNullOrEmpty(data);
    }

    public static <T extends Enum<T>> boolean isInEnum(Class<T> enumClass, String name) {
        try {
            Enum.valueOf(enumClass, name);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        } catch (NullPointerException exception) {
            return false;
        }
    }

    public static boolean isValidDate(String date) {
        return DateHelper.isValidDate(date);
    }
}