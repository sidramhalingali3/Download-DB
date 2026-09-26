package com.example.dbdownloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller responsible for generating and downloading SQL backups
 * from a live TiDB / MySQL compatible database.
 *
 * SECURITY & READ-ONLY NOTICE:
 * 1. This controller only executes `mysqldump`, which strictly performs SELECT/dump operations.
 * 2. It does NOT execute any INSERT, UPDATE, DELETE, DROP, or ALTER statements against the database.
 * 3. Database credentials (especially passwords) are injected securely via environment variables
 *    and are NEVER logged, returned in API responses, or exposed to the client.
 * 4. The database password is passed to `mysqldump` via the `MYSQL_PWD` process environment variable,
 *    preventing password exposure in process listing tables (e.g. ps / Task Manager).
 */
@RestController
public class DatabaseController {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseController.class);

    @Value("${DB_HOST:}")
    private String dbHost;

    @Value("${DB_PORT:4000}")
    private String dbPort;

    @Value("${DB_NAME:}")
    private String dbName;

    @Value("${DB_USERNAME:}")
    private String dbUsername;

    @Value("${DB_PASSWORD:}")
    private String dbPassword;

    @Value("${app.mysqldump.path:mysqldump}")
    private String mysqldumpPath;

    /**
     * Endpoint to generate and download the database SQL backup.
     *
     * GET /download-db
     *
     * @return ResponseEntity with SQL file binary download or useful HTTP error response.
     */
    @GetMapping("/download-db")
    public ResponseEntity<?> downloadDatabase() {

        // 1. Security & Configuration Validation: Check required environment variables
        if (isNullOrBlank(dbHost) || isNullOrBlank(dbName) || isNullOrBlank(dbUsername)) {
            logger.error("Database download aborted: Environment variables DB_HOST, DB_NAME, or DB_USERNAME are not set.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Database backup failed: Server database configuration is incomplete. Please set DB_HOST, DB_NAME, and DB_USERNAME environment variables.");
        }

        File tempSqlFile = null;
        File tempErrFile = null;

        try {
            // 2. Generate temporary files for storing SQL output and process error messages
            tempSqlFile = File.createTempFile("tidb_backup_", ".sql");
            tempErrFile = File.createTempFile("tidb_backup_err_", ".log");

            // 3. Construct mysqldump command
            // Note: We intentionally DO NOT include -p or --password in the argument list
            // to avoid exposing credentials in shell history or process monitor lists.
            List<String> command = new ArrayList<>();
            command.add(mysqldumpPath);
            command.add("-h");
            command.add(dbHost);
            command.add("-P");
            command.add(dbPort);
            command.add("-u");
            command.add(dbUsername);

            // Read-only flags & backup scope flags
            // Removed --single-transaction because it causes "SAVEPOINT sp does not exist" on TiDB Serverless
            command.add("--quick");              // Forces mysqldump to retrieve rows one by one (saves memory)
            command.add("--routines");           // Dump stored procedures and functions if supported
            command.add("--triggers");           // Dump triggers
            command.add(dbName);

            ProcessBuilder pb = new ProcessBuilder(command);

            // SECURITY: Pass password securely through the environment variable MYSQL_PWD
            if (!isNullOrBlank(dbPassword)) {
                pb.environment().put("MYSQL_PWD", dbPassword);
            }

            // Redirect process outputs
            pb.redirectOutput(tempSqlFile);
            pb.redirectError(tempErrFile);

            logger.info("Initiating database dump for host '{}', database '{}' using mysqldump executable '{}'.", dbHost, dbName, mysqldumpPath);

            // 4. Start execution of mysqldump
            Process process = pb.start();
            int exitCode = process.waitFor();

            // Read error log if any error occurred
            String errorMessage = readAndSanitizeFileContent(tempErrFile);

            // 5. Verify process result and output file size
            if (exitCode != 0 || tempSqlFile.length() == 0) {
                logger.error("mysqldump process failed with exit code: {}", exitCode);
                cleanUpQuietly(tempSqlFile);
                cleanUpQuietly(tempErrFile);

                String clientMessage = "Database backup failed. ";
                if (!errorMessage.isBlank()) {
                    clientMessage += "Error detail: " + sanitizeErrorOutput(errorMessage);
                } else if (tempSqlFile.length() == 0) {
                    clientMessage += "The generated backup file was empty. Please check database permissions.";
                }

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(clientMessage);
            }

            // Clean up temporary error log file as process succeeded
            cleanUpQuietly(tempErrFile);

            // 6. Generate formatted download file name (e.g. tidb_backup_20260926_233000.sql)
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String downloadFilename = String.format("FinanceManagementSystemDB_%s.sql", timestamp);

            // 7. Prepare InputStreamResource with auto-delete on close to safely deliver download and delete temp file
            File finalSqlFile = tempSqlFile;
            InputStreamResource resource = new InputStreamResource(new java.io.FileInputStream(finalSqlFile) {
                @Override
                public void close() throws IOException {
                    super.close();
                    // Safe cleanup of temporary file after streaming completes or fails
                    cleanUpQuietly(finalSqlFile);
                }
            });

            logger.info("Successfully generated database backup of size {} bytes. Streaming file '{}' to browser.",
                    tempSqlFile.length(), downloadFilename);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadFilename + "\"")
                    .contentType(MediaType.parseMediaType("application/sql"))
                    .contentLength(tempSqlFile.length())
                    .body(resource);

        } catch (IOException e) {
            cleanUpQuietly(tempSqlFile);
            cleanUpQuietly(tempErrFile);
            logger.error("IO Error executing mysqldump: ", e);

            String detail = e.getMessage();
            if (detail != null && detail.contains("Cannot run program")) {
                detail = "Executable '" + mysqldumpPath + "' not found. Please ensure mysqldump is installed and added to PATH or MYSQLDUMP_PATH variable.";
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Database backup failed: " + sanitizeErrorOutput(detail));

        } catch (InterruptedException e) {
            cleanUpQuietly(tempSqlFile);
            cleanUpQuietly(tempErrFile);
            Thread.currentThread().interrupt();
            logger.error("mysqldump process was interrupted.", e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Database backup failed: Process was interrupted.");
        }
    }

    /**
     * Helper to check if string is null, empty, or whitespace.
     */
    private boolean isNullOrBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Reads text file content and redacts sensitive information.
     */
    private String readAndSanitizeFileContent(File file) {
        if (file == null || !file.exists() || file.length() == 0) {
            return "";
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return sanitizeErrorOutput(content);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * SECURITY HELPER: Sanitizes error messages to guarantee no password leak occurs in error responses.
     */
    private String sanitizeErrorOutput(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String sanitized = input;
        if (!isNullOrBlank(dbPassword)) {
            sanitized = sanitized.replace(dbPassword, "******");
        }
        return sanitized.trim();
    }

    /**
     * Deletes temporary file without throwing exceptions.
     */
    private void cleanUpQuietly(File file) {
        if (file != null && file.exists()) {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (Exception ignored) {
                // Ignore cleanup errors
            }
        }
    }
}
