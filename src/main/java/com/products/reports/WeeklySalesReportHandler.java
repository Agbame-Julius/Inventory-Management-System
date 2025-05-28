package com.products.reports;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.products.model.Product;
import com.products.request.SaleLineItem;
import com.products.model.Sales;
import com.products.repository.ProductRepository;
import com.products.repository.SalesRepository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
//import software.amazon.awssdk.services.s3.presigned.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class WeeklySalesReportHandler implements RequestHandler<ScheduledEvent, Void> {

    private final DynamoDbEnhancedClient enhancedClient;
    private final SalesRepository salesRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;
    private final S3Client s3Client;
    private final SesClient sesClient;
    private final String salesTable;
    private final String productTable;
    private final String bucketName;
    private final String adminEmail;
    private final String emailSender;

    public WeeklySalesReportHandler() {
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
        enhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
        salesTable = System.getenv("SALES_TABLE");
        productTable = System.getenv("PRODUCT_TABLE");
        bucketName = System.getenv("REPORT_BUCKET");
        adminEmail = System.getenv("ADMIN_EMAIL");
        emailSender = System.getenv("EMAIL_SENDER");
        salesRepository = new SalesRepository(enhancedClient, salesTable);
        productRepository = new ProductRepository(enhancedClient, productTable);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        s3Client = S3Client.builder().build();
        sesClient = SesClient.builder().build();
    }

    @Override
    public Void handleRequest(ScheduledEvent event, Context context) {
        try {
            // Calculate date range for the previous week (Monday to Sunday)
            LocalDate today = LocalDate.now();
            // Find the most recent Sunday (end of the week)
            LocalDate endDate = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            // If today is Sunday, use the current Sunday; otherwise, use the previous Sunday
            if (today.getDayOfWeek() != DayOfWeek.SUNDAY) {
                endDate = endDate.minusDays(7);
            }
            // Start from Monday of that week
            LocalDate startDate = endDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            String startDateStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String endDateStr = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

            // Query SalesTable for sales within the date range
            List<Sales> sales = salesRepository.findByDateRange(startDate, endDate);

            // Generate report data
            List<ReportItem> reportItems = new ArrayList<>();
            for (Sales sale : sales) {
                LocalDate  dateSold = sale.getDateSold();
                for (SaleLineItem item : sale.getItems()) {
                    Product product = productRepository.findByProductId(item.getProductId());
                    if (product != null) {
                        reportItems.add(new ReportItem(
                                product.getProductName(),
                                product.getCategoryName(),
                                item.getQuantitySold(),
                                dateSold,
                                item.getTotalPrice()
                        ));
                    }
                }
            }

            // Generate CSV
            String csvContent = generateCsv(reportItems);
            String csvKey = "reports/weekly-sales-report-" + startDateStr + "-to-" + endDateStr + ".csv";
            uploadToS3(csvContent, csvKey, "text/csv");

            // Generate presigned URL for CSV download
            String presignedUrl = generatePresignedUrl(csvKey, context);

            // Send email with download link
            sendEmail(presignedUrl, startDateStr, endDateStr, context);

            context.getLogger().log("Weekly sales report generated and emailed successfully for " + startDateStr + " to " + endDateStr);
        } catch (Exception e) {
            context.getLogger().log("Error generating report: " + e.getMessage());
        }
        return null;
    }

    private String generateCsv(List<ReportItem> reportItems) {
        StringBuilder csv = new StringBuilder();
        csv.append("Item Sold,Category,Quantity,Revenue, Date-Sold\n");
        for (ReportItem item : reportItems) {
            csv.append(String.format("\"%s\",\"%s\",%d,%.2f,\"%s\"\n",
                    item.itemName.replace("\"", "\"\""), // Escape quotes for CSV
                    item.categoryName.replace("\"", "\"\""),
                    item.quantity,
                    item.revenue,
                    item.dateSold.format(DateTimeFormatter.ISO_LOCAL_DATE)));
        }
        return csv.toString();
    }

    private void uploadToS3(String content, String key, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromString(content));
    }

    private String generatePresignedUrl(String key, Context context) {
        var presignedRequest = s3Client.utilities().getUrl(builder -> builder
                .bucket(bucketName)
                .key(key)
                .build());
        return presignedRequest.toString();
    }

    private void sendEmail(String presignedUrl, String startDate, String endDate, Context context) {
        try {
            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .destination(Destination.builder().toAddresses(adminEmail).build())
                    .message(Message.builder()
                            .subject(Content.builder().data("Weekly Sales Report (" + startDate + " to " + endDate + ")").build())
                            .body(Body.builder()
                                    .text(Content.builder().data("The weekly sales report for " + startDate + " to " + endDate + " is available for download at: " + presignedUrl).build())
                                    .build())
                            .build())
                            .source(emailSender)
                            .build();
            sesClient.sendEmail(emailRequest);
        } catch (Exception e) {
            context.getLogger().log("Error sending email: " + e.getMessage());
        }
    }

    // Inner class to hold report data
    private static class ReportItem {
        String itemName;
        String categoryName;
        int quantity;
        LocalDate dateSold;
        double revenue;

        ReportItem(String itemName, String categoryName, int quantity, LocalDate dateSold,double revenue) {
            this.itemName = itemName;
            this.categoryName = categoryName;
            this.quantity = quantity;
            this.dateSold = dateSold;
            this.revenue = revenue;
        }


    }
}
