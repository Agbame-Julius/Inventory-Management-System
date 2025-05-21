package com.products.crud;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.products.model.Category;
import com.products.model.Product;
import com.products.request.ProductRequest;
import com.products.response.ProductResponse;
import com.products.response.SkippedProduct;
import com.products.utils.CognitoUtil;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.regions.Region;

import java.time.LocalDate;
import java.util.*;

public class CreateProductHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbEnhancedClient enhancedClient;
    private final ObjectMapper objectMapper;
    private final DynamoDbTable<Product> productTable;
    private final DynamoDbTable<Category> categoryTable;
    private final String productTableName;
    private final String categoryTableName;

    public CreateProductHandler() {
        this.productTableName = System.getenv("PRODUCT_TABLE");
        this.categoryTableName = System.getenv("CATEGORY_TABLE");

        if (this.productTableName == null || this.categoryTableName == null) {
            System.err.println("Missing environment variables: PRODUCT_TABLE=" + productTableName + ", CATEGORY_TABLE=" + categoryTableName);
            this.enhancedClient = null;
            this.productTable = null;
            this.categoryTable = null;
        } else {
            this.enhancedClient = DynamoDbEnhancedClient.builder()
                    .dynamoDbClient(DynamoDbClient.builder()
                            .region(Region.of(System.getenv("REGION")))
                            .build())
                    .build();
            this.productTable = enhancedClient.table(productTableName, TableSchema.fromBean(Product.class));
            this.categoryTable = enhancedClient.table(categoryTableName, TableSchema.fromBean(Category.class));
        }
        this.objectMapper = new ObjectMapper();
    }

    // Constructor for testing
    public CreateProductHandler(DynamoDbEnhancedClient enhancedClient, ObjectMapper objectMapper, String productTableName, String categoryTableName) {
        this.enhancedClient = enhancedClient;
        this.objectMapper = objectMapper;
        this.productTableName = productTableName;
        this.categoryTableName = categoryTableName;
        this.productTable = enhancedClient.table(productTableName, TableSchema.fromBean(Product.class));
        this.categoryTable = enhancedClient.table(categoryTableName, TableSchema.fromBean(Category.class));
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("Starting request processing. Input: " + (input.getBody() != null ? input.getBody() : "null"));
        context.getLogger().log("Product table: " + productTableName + ", Category table: " + categoryTableName);

        if (enhancedClient == null || productTable == null || categoryTable == null) {
            context.getLogger().log("Lambda initialization failed due to missing environment variables");
            return createResponse(500, new ProductResponse(0, Collections.emptyList(), "Lambda initialization failed"), context);
        }

        try {
            if(!CognitoUtil.isAdmin(input)){
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(401)
                        .withBody("You're not authorized to perform this operation");
            }
            // Parse request body
            ProductRequest request;
            String body = input.getBody();
            if (body == null || body.isEmpty()) {
                context.getLogger().log("Empty request body");
                return createResponse(400, new ProductResponse(0, Collections.emptyList(), "Empty request body"), context);
            }

            try {
                request = objectMapper.readValue(body, ProductRequest.class);
            } catch (Exception e) {
                context.getLogger().log("Failed to parse as ProductRequest, attempting single product: " + e.getMessage());
                ProductRequest.ProductInput singleProduct = objectMapper.readValue(body, ProductRequest.ProductInput.class);
                request = new ProductRequest(Collections.singletonList(singleProduct));
            }


            if (request.getProducts() == null || request.getProducts().isEmpty()) {
                context.getLogger().log("No products provided in request");
                return createResponse(400, new ProductResponse(0, Collections.emptyList(), "No products provided"), context);
            }

            context.getLogger().log("Processing " + request.getProducts().size() + " products");

            // Validate and process products
            List<Product> validProducts = validateProducts(request.getProducts(), context);
            List<SkippedProduct> skippedProducts = new ArrayList<>();

            // Check for duplicates and add valid products
            int addedCount = createProducts(validProducts, skippedProducts, context);

            ProductResponse response = new ProductResponse(addedCount, skippedProducts);
            context.getLogger().log("Completed processing. Added: " + addedCount + ", Skipped: " + skippedProducts.size());
//            return createResponse(201, response, context);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(201)
                    .withBody(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            context.getLogger().log("Error processing request: " + e.getMessage());
            return createResponse(500, new ProductResponse(0, Collections.emptyList(), "Internal server error: " + e.getMessage()), context);
        }
    }

    private List<Product> validateProducts(List<ProductRequest.ProductInput> products, Context context) {
        List<Product> validProducts = new ArrayList<>();
        List<SkippedProduct> skippedProducts = new ArrayList<>();

        for (ProductRequest.ProductInput input : products) {
            try {
                context.getLogger().log("Validating product: " + input.getProductName());

                // Validate required fields
                if (input.getProductName() == null || input.getProductName().isEmpty() || input.getCategoryId() == null || input.getCategoryId().isEmpty()) {
                    context.getLogger().log("Skipping product due to missing productName or categoryId");
                    skippedProducts.add(new SkippedProduct(input.getProductName(), input.getCategoryId(), "Missing productName or categoryId"));
                    continue;
                }

                // Validate category exists
                if (!categoryExists(input.getCategoryId(), input.getCategoryName(), context)) {
                    context.getLogger().log("Skipping product " + input.getProductName() + ": Invalid category");
                    skippedProducts.add(new SkippedProduct(input.getProductName(), input.getCategoryId(), "Invalid category"));
                    continue;
                }

                // Create product object
                Product product = Product.builder()
                        .productId(UUID.randomUUID().toString())
                        .productName(input.getProductName())
                        .unitCostPrice(input.getUnitCostPrice())
                        .unitSellingPrice(input.getUnitSellingPrice())
                        .quantity(input.getQuantity())
                        .categoryId(input.getCategoryId())
                        .categoryName(input.getCategoryName())
                        .dateAdded(LocalDate.now())
                        .dateUpdated(LocalDate.now())
                        .totalPrice(input.getUnitCostPrice() * input.getQuantity())
                        .totalSellingPrice(input.getUnitSellingPrice() * input.getQuantity())
                        .build();

                validProducts.add(product);
                context.getLogger().log("Product " + input.getProductName() + " validated successfully");
            } catch (Exception e) {
                context.getLogger().log("Error validating product " + (input.getProductName() != null ? input.getProductName() : "unknown") + ": " + e.getMessage());
                skippedProducts.add(new SkippedProduct(input.getProductName(), input.getCategoryId(), "Validation error: " + e.getMessage()));
            }
        }

        if (!skippedProducts.isEmpty()) {
            context.getLogger().log("Skipped products: " + skippedProducts.size());
        }
        return validProducts;
    }

    private boolean categoryExists(String categoryId, String categoryName, Context context) {
        try {
            context.getLogger().log("Checking category existence: ID=" + categoryId + ", Name=" + categoryName);
            if (categoryId != null && categoryName != null) {
                Key key = Key.builder()
                        .partitionValue(categoryId)
                        .sortValue(categoryName)
                        .build();
                Category category = categoryTable.getItem(key);
                boolean exists = category != null;
                context.getLogger().log("Category ID=" + categoryId + ", Name=" + categoryName + " exists: " + exists);
                return exists;
            } else if (categoryName != null) {
                DynamoDbIndex<Category> categoryNameIndex = categoryTable.index("CategoryNameIndex");
                QueryConditional query = QueryConditional.keyEqualTo(Key.builder().partitionValue(categoryName).build());
                boolean exists = categoryNameIndex.query(query).stream().findFirst().isPresent();
                context.getLogger().log("Category Name " + categoryName + " exists: " + exists);
                return exists;
            }
            context.getLogger().log("No valid category ID or name provided");
            return false;
        } catch (DynamoDbException e) {
            context.getLogger().log("Error checking category existence: " + e.getMessage());
            return false;
        }
    }

    private int createProducts(List<Product> products, List<SkippedProduct> skippedProducts, Context context) {
        int addedCount = 0;

        for (Product product : products) {
            try {
                context.getLogger().log("Processing product: " + product.getProductName() + " in category " + product.getCategoryId());

                // Check for duplicate product name in category using GSI
                DynamoDbIndex<Product> categoryIndex = productTable.index("CategoryIndex");
                QueryConditional query = QueryConditional.keyEqualTo(
                        Key.builder()
                                .partitionValue(product.getCategoryId())
                                .sortValue(product.getProductName())
                                .build());
                boolean exists = categoryIndex.query(query).stream().flatMap(page -> page.items().stream()).findFirst().isPresent();
                context.getLogger().log("Duplicate check for " + product.getProductName() + ": exists=" + exists);

                if (exists) {
                    context.getLogger().log("Updating existing product: " + product.getProductName());
                    // Fetch existing product to get productId
                    Product existingProduct = categoryIndex.query(query).stream()
                            .flatMap(page -> page.items().stream())
                            .findFirst()
                            .orElse(null);
                    if (existingProduct != null) {
                        context.getLogger().log("Found existing product with productId: " + existingProduct.getProductId());
                        product.setProductId(existingProduct.getProductId()); // Retain original productId
                        product.setDateUpdated(LocalDate.now());
                        productTable.putItem(product); // Overwrite existing item
                        addedCount++;
                        context.getLogger().log("Successfully updated product: " + product.getProductName());
                    } else {
                        context.getLogger().log("Failed to fetch existing product for update: " + product.getProductName());
                        skippedProducts.add(new SkippedProduct(product.getProductName(), product.getCategoryId(), "Failed to update: Product not found"));
                    }
                    continue;
                }

                context.getLogger().log("Attempting to save new product: " + product.getProductName());
                productTable.putItem(product);
                addedCount++;
                context.getLogger().log("Successfully saved product: " + product.getProductName());

            } catch (DynamoDbException e) {
                context.getLogger().log("Error processing product " + product.getProductName() + ": " + e.getMessage());
                skippedProducts.add(new SkippedProduct(product.getProductName(), product.getCategoryId(), "Failed to create/update: " + e.getMessage()));
            }
        }

        return addedCount;
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, ProductResponse response, Context context) {
        try {
            context.getLogger().log("Creating response: status=" + statusCode + ", addedCount=" + response.getAddedCount() + ", skipped=" + response.getSkippedProducts().size());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(Collections.singletonMap("Content-Type", "application/json"))
                    .withBody(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            context.getLogger().log("Error creating response: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\": \"Error creating response\"}");
        }
    }
}