package com.airtribe.taskmanager.parser;

import java.io.IOException;

public interface TaskParser {
    ParsedTask parse(String text, String today) throws IOException;
};
