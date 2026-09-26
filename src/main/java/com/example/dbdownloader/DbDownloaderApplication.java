package com.example.dbdownloader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for TiDB DB Downloader.
 *
 * This Spring Boot application allows exporting a live MySQL/TiDB compatible
 * database to a SQL dump file for backup download.
 */
@SpringBootApplication
public class DbDownloaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbDownloaderApplication.class, args);
    }
}
