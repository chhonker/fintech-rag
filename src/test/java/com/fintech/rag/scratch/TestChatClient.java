package com.fintech.rag.scratch;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;

import java.util.List;

public class TestChatClient {
    public static void main(String[] args) {
        ChatClient chatClient = null; // Mock
        var responseSpec = chatClient.prompt().user("hello").call();
        // Try chatClientResponse() or something similar
        var resp = responseSpec.chatResponse();
        // check metadata
        System.out.println(resp.getMetadata());
    }
}
