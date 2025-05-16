package com.products.response;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class CloudFormationResponseSender {
    public static void sendResponse(String responseUrl, String status, String reason, String physicalResourceId, String stackId, String requestId, String logicalResourceId, Map<String, Object> data) throws Exception {
        String responseBody = String.format(
                "{ \"Status\": \"%s\", \"Reason\": \"%s\", \"PhysicalResourceId\": \"%s\", \"StackId\": \"%s\", \"RequestId\": \"%s\", \"LogicalResourceId\": \"%s\", \"Data\": %s }",
                status,
                reason,
                physicalResourceId,
                stackId,
                requestId,
                logicalResourceId,
                data != null ? new ObjectMapper().writeValueAsString(data) : "{}"
        );

        URL url = new URL(responseUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("PUT");
        byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(out.length);
        connection.setRequestProperty("Content-Type", "");
        connection.connect();
        try (OutputStream os = connection.getOutputStream()) {
            os.write(out);
        }
        connection.getInputStream().close();
    }
}

