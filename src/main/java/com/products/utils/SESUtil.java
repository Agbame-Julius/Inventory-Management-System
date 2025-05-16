package com.products.utils;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

public class SESUtil {
    private static final String region = System.getenv("REGION");
    private static final SesClient sesClient = SesClient.builder()
            .region(Region.of(region))
            .build();
    private static final String senderEmail = System.getenv("EMAIL_SENDER");
    ;

    public static void sendEmail(String recipientEmail, String subject, String body) {
        try {
            Destination destination = Destination.builder()
                    .toAddresses(recipientEmail)
                    .build();

            Content subjectContent = Content.builder()
                    .data(subject)
                    .charset("UTF-8")
                    .build();

            Content htmlBody = Content.builder()
                    .data(body)
                    .charset("UTF-8")
                    .build();

            Content textBody = Content.builder()
                    .data(stripHtml(body))
                    .charset("UTF-8")
                    .build();

            Body emailBody = Body.builder()
                    .html(htmlBody)
                    .text(textBody)
                    .build();

            Message message = Message.builder()
                    .subject(subjectContent)
                    .body(emailBody)
                    .build();

            SendEmailRequest request = SendEmailRequest.builder()
                    .source(senderEmail)
                    .destination(destination)
                    .message(message)
                    .build();

            sesClient.sendEmail(request);
        } catch (SesException e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    private static String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

}
