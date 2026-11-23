package com.sumasoft.tracking.imports;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Upload size limits for the import files (Step 32b).
 * One file may be at most 25 MB (the full Codility workbook with all months is about 9 MB).
 * Bigger uploads are refused before they reach our code.
 */
@Configuration
public class ImportUploadConfig {

    public static final long MAX_FILE_BYTES = 25L * 1024 * 1024;

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        // (temporary folder = default, max file size, max whole request size, keep in memory up to)
        return new MultipartConfigElement("", MAX_FILE_BYTES, MAX_FILE_BYTES + 1024 * 1024, 0);
    }
}