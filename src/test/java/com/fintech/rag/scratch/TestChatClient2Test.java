package com.fintech.rag.scratch;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
// import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;

import java.lang.reflect.Method;

public class TestChatClient2Test {
    @Test
    public void test() throws Exception {
        System.out.println("Checking ChatClient.CallResponseSpec methods:");
        for (Method m : ChatClient.CallResponseSpec.class.getMethods()) {
            System.out.println(m.getName());
        }
        
        System.out.println("Checking org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor:");
        try {
            Class<?> c = Class.forName("org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor");
            System.out.println("Found it at org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor");
        } catch (Exception e) {
            System.out.println("Not found at org.springframework.ai.chat.client.advisor");
        }
        
        try {
            Class<?> c = Class.forName("org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor");
            System.out.println("Found it at org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor");
            for (java.lang.reflect.Field f : c.getFields()) {
                 System.out.println(f.getName());
            }
        } catch (Exception e) {
            System.out.println("Not found at org.springframework.ai.chat.client.advisor.vectorstore");
        }
        
        System.out.println("Checking org.springframework.ai.chat.client.advisor.api.QuestionAnswerAdvisor:");
        try {
            Class<?> c = Class.forName("org.springframework.ai.chat.client.advisor.api.QuestionAnswerAdvisor");
            System.out.println("Found it at org.springframework.ai.chat.client.advisor.api.QuestionAnswerAdvisor");
        } catch (Exception e) {
            System.out.println("Not found at org.springframework.ai.chat.client.advisor.api");
        }
    }
}
