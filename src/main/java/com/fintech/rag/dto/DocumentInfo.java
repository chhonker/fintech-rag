package com.fintech.rag.dto;

import java.util.List;

public record DocumentInfo(String fileName, List<String> categories, String fileHash) {
}
