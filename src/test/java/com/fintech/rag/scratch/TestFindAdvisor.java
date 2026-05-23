package com.fintech.rag.scratch;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.FileInputStream;

public class TestFindAdvisor {
    @Test
    public void test() throws Exception {
        String cp = System.getProperty("java.class.path");
        for (String p : cp.split(File.pathSeparator)) {
            if (p.endsWith(".jar")) {
                try (ZipInputStream zip = new ZipInputStream(new FileInputStream(p))) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (entry.getName().contains("QuestionAnswerAdvisor.class")) {
                            System.out.println("FOUND: " + entry.getName() + " in " + p);
                        }
                    }
                }
            }
        }
    }
}
