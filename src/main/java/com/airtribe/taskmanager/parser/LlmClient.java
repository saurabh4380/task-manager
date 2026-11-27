package com.airtribe.taskmanager.parser;

import java.io.IOException;

public interface LlmClient {

    String send(String prompt) throws IOException;

    /**
     * Sends a prompt that defines its own output format instead of the
     * task-parsing format used by {@link #send(String)}.
     *
     * <p>Fake clients can leave this default implementation in place.
     */
    default String sendRaw(String prompt) throws IOException {
        return send(prompt);
    }

}
