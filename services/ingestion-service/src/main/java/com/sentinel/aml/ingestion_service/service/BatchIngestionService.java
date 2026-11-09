package com.sentinel.aml.ingestion_service.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface BatchIngestionService {
    String processBatchAsync(MultipartFile file) throws IOException;
}