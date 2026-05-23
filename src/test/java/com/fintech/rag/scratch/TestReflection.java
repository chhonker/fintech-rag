package com.fintech.rag.scratch;

import org.springframework.ai.chat.client.ChatClient;
import java.lang.reflect.Method;

public class TestReflection {
    public static void main(String[] args) {
        for (Method m : ChatClient.CallResponseSpec.class.getMethods()) {
            System.out.println(m.getName());
        }
    }
}
