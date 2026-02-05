package examples;

import com.emailverify.sdk.EmailVerifyClient;
import com.emailverify.sdk.exception.*;
import com.emailverify.sdk.model.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Examples demonstrating file upload for asynchronous email verification,
 * job status tracking, and downloading results.
 */
public class FileUploadExample {

    public static void main(String[] args) {
        // Get API key from environment variable
        String apiKey = System.getenv("EMAILVERIFY_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Please set the EMAILVERIFY_API_KEY environment variable");
            System.exit(1);
        }

        try (var client = EmailVerifyClient.builder(apiKey).build()) {
            // Create a sample CSV file for testing
            File testFile = createSampleCsvFile();

            // Run examples
            fileUploadExample(client, testFile);

            // Cleanup
            testFile.delete();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create a sample CSV file for testing.
     */
    private static File createSampleCsvFile() throws IOException {
        File file = File.createTempFile("emails", ".csv");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("email,name,company\n");
            writer.write("test1@gmail.com,John Doe,Acme Inc\n");
            writer.write("test2@yahoo.com,Jane Smith,Tech Corp\n");
            writer.write("invalid@nonexistent123.com,Bob Wilson,StartupX\n");
            writer.write("support@example.com,Support Team,Example Ltd\n");
            writer.write("noreply@tempmail.com,No Reply,TempCo\n");
        }
        return file;
    }

    /**
     * File upload and async verification example.
     */
    private static void fileUploadExample(EmailVerifyClient client, File file) throws EmailVerifyException, IOException {
        System.out.println("\n=== File Upload Example ===");

        // Upload file for verification
        System.out.println("Uploading file: " + file.getName());
        FileUploadResponse upload = client.uploadFile(file);

        System.out.println("Task ID: " + upload.taskId());
        System.out.println("Status: " + upload.status());
        System.out.println("Message: " + upload.message());
        System.out.println("File Name: " + upload.fileName());
        System.out.println("File Size: " + upload.fileSize() + " bytes");
        System.out.println("Estimated Count: " + upload.estimatedCount());
        System.out.println("Unique Emails: " + upload.uniqueEmails());
        System.out.println("Total Rows: " + upload.totalRows());
        System.out.println("Email Column: " + upload.emailColumn());
        System.out.println("Status URL: " + upload.statusUrl());
        System.out.println("Created At: " + upload.createdAt());

        String taskId = upload.taskId();

        // Get job status (polling)
        System.out.println("\n--- Checking Job Status ---");
        FileJobResponse status = client.getFileJobStatus(taskId);
        printJobStatus(status);

        // Get job status with long-polling (waits up to 60 seconds)
        System.out.println("\n--- Checking with Long-Polling (60s timeout) ---");
        FileJobResponse statusLongPoll = client.getFileJobStatus(taskId, 60);
        printJobStatus(statusLongPoll);

        // Wait for completion using the built-in helper method
        System.out.println("\n--- Waiting for Completion ---");
        FileJobResponse completed = client.waitForFileJobCompletion(taskId);
        printJobStatus(completed);

        if ("completed".equals(completed.status())) {
            // Download results
            downloadResultsExample(client, taskId);
        } else if ("failed".equals(completed.status())) {
            System.err.println("Job failed!");
        }
    }

    /**
     * Print job status details.
     */
    private static void printJobStatus(FileJobResponse status) {
        System.out.println("Job ID: " + status.jobId());
        System.out.println("Status: " + status.status());
        System.out.println("Progress: " + status.progressPercent() + "%");
        System.out.println("Processed: " + status.processedEmails() + "/" + status.totalEmails());

        if ("completed".equals(status.status())) {
            System.out.println("\n--- Results Summary ---");
            System.out.println("Valid Emails: " + status.validEmails());
            System.out.println("Invalid Emails: " + status.invalidEmails());
            System.out.println("Unknown Emails: " + status.unknownEmails());
            System.out.println("Role Emails: " + status.roleEmails());
            System.out.println("Catchall Emails: " + status.catchallEmails());
            System.out.println("Disposable Emails: " + status.disposableEmails());
            System.out.println("Credits Used: " + status.creditsUsed());
            System.out.println("Process Time: " + status.processTimeSeconds() + " seconds");
            System.out.println("Download URL: " + status.downloadUrl());
        }
    }

    /**
     * Download results example with filters.
     */
    private static void downloadResultsExample(EmailVerifyClient client, String jobId) throws EmailVerifyException, IOException {
        System.out.println("\n=== Download Results Example ===");

        // Get download URL for all results
        String allResultsUrl = client.getFileResultsUrl(jobId);
        System.out.println("All Results URL: " + allResultsUrl);

        // Download all results
        byte[] allResults = client.downloadFileResults(jobId);
        System.out.println("Downloaded " + allResults.length + " bytes");

        // Save to file
        Path outputPath = Path.of("verification_results_all.csv");
        Files.write(outputPath, allResults);
        System.out.println("Saved to: " + outputPath.toAbsolutePath());

        // Download only valid emails
        System.out.println("\n--- Download Only Valid Emails ---");
        ResultFilters validOnly = ResultFilters.builder()
            .valid(true)
            .build();

        String validResultsUrl = client.getFileResultsUrl(jobId, validOnly);
        System.out.println("Valid Results URL: " + validResultsUrl);

        byte[] validResults = client.downloadFileResults(jobId, validOnly);
        Path validOutputPath = Path.of("verification_results_valid.csv");
        Files.write(validOutputPath, validResults);
        System.out.println("Saved valid emails to: " + validOutputPath.toAbsolutePath());

        // Download valid and risky emails (good for marketing)
        System.out.println("\n--- Download Valid and Risky Emails ---");
        ResultFilters marketingFilter = ResultFilters.builder()
            .valid(true)
            .risky(true)
            .build();

        byte[] marketingResults = client.downloadFileResults(jobId, marketingFilter);
        Path marketingOutputPath = Path.of("verification_results_marketing.csv");
        Files.write(marketingOutputPath, marketingResults);
        System.out.println("Saved marketing list to: " + marketingOutputPath.toAbsolutePath());

        // Download catch-all and role emails for review
        System.out.println("\n--- Download Catch-all and Role Emails for Review ---");
        ResultFilters reviewFilter = ResultFilters.builder()
            .catchall(true)
            .role(true)
            .build();

        byte[] reviewResults = client.downloadFileResults(jobId, reviewFilter);
        Path reviewOutputPath = Path.of("verification_results_review.csv");
        Files.write(reviewOutputPath, reviewResults);
        System.out.println("Saved review list to: " + reviewOutputPath.toAbsolutePath());

        // Cleanup created files
        Files.deleteIfExists(outputPath);
        Files.deleteIfExists(validOutputPath);
        Files.deleteIfExists(marketingOutputPath);
        Files.deleteIfExists(reviewOutputPath);
    }

    /**
     * Advanced file upload with all options.
     */
    private static void advancedFileUploadExample(EmailVerifyClient client, File file) throws EmailVerifyException {
        System.out.println("\n=== Advanced File Upload Example ===");

        // Upload with all options
        FileUploadResponse upload = client.uploadFile(
            file,
            true,           // checkSmtp - perform SMTP verification
            "email",        // emailColumn - specify the column name (auto-detected if null)
            true            // preserveOriginal - keep original columns in result file
        );

        System.out.println("Task ID: " + upload.taskId());
        System.out.println("Unique Emails: " + upload.uniqueEmails());

        // Wait with custom timeout
        System.out.println("\n--- Waiting with 30-minute timeout ---");
        FileJobResponse completed = client.waitForFileJobCompletion(
            upload.taskId(),
            Duration.ofMinutes(30)
        );

        System.out.println("Final Status: " + completed.status());

        if ("completed".equals(completed.status())) {
            System.out.println("Job completed successfully!");
            System.out.println("Valid: " + completed.validEmails());
            System.out.println("Invalid: " + completed.invalidEmails());
        }
    }

    /**
     * Example showing manual polling (alternative to waitForFileJobCompletion).
     */
    private static void manualPollingExample(EmailVerifyClient client, String taskId) throws EmailVerifyException, InterruptedException {
        System.out.println("\n=== Manual Polling Example ===");

        int maxAttempts = 60;
        int attempt = 0;

        while (attempt < maxAttempts) {
            // Use long-polling with 60 second timeout
            FileJobResponse status = client.getFileJobStatus(taskId, 60);

            System.out.printf("Attempt %d: Status=%s, Progress=%d%%%n",
                attempt + 1,
                status.status(),
                status.progressPercent()
            );

            if ("completed".equals(status.status())) {
                System.out.println("Job completed!");
                System.out.println("Valid: " + status.validEmails());
                System.out.println("Invalid: " + status.invalidEmails());
                return;
            }

            if ("failed".equals(status.status())) {
                System.err.println("Job failed!");
                return;
            }

            attempt++;
            // Long-polling already waits, but add a small delay between attempts
            Thread.sleep(1000);
        }

        System.err.println("Job did not complete within expected time");
    }
}
