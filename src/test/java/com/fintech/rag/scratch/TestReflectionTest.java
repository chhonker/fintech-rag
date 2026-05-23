package com.fintech.rag.scratch;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

public class TestReflectionTest {
    @Test
    public void test() {
        System.out.println("METHODS:");
        for (Method m : ChatClient.CallResponseSpec.class.getMethods()) {
            System.out.println(m.getName());
        }
        System.out.println("END METHODS");
    }
}
