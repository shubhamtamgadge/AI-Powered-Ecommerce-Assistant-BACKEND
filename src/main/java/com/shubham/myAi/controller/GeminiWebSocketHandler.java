package com.shubham.myAi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shubham.myAi.entity.Product;
import com.shubham.myAi.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;

@Component
public class GeminiWebSocketHandler extends TextWebSocketHandler {


    @Value("${gemini.api.key}")
    private String apiKey;


    @Autowired
    private ProductRepository productRepository;

    private final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String userInput = message.getPayload().trim().toLowerCase();
        System.out.println("🔹 Original User Input: " + userInput);

        String response;
        String isSqlQuery = isEcommerceQuery(userInput);

        System.out.println("printing the isEcommerce "+ isSqlQuery);


        if ("No".equalsIgnoreCase(isSqlQuery.trim())) {
            System.out.println("inside the non-sql query block");
            response = callGeminiForAnswer(userInput);

        } else {

            System.out.println("it is a ecommerce query");
            //received query in response, generate output for the query
            List<Product> products  = handleEcommerceQuery(isSqlQuery);

            if (products.isEmpty()) {
                response = "No Products Found";
            }
            else{
                // Correctly format the response
                StringBuilder showProducts = new StringBuilder("\uD83D\uDED2 Showing all related results - <br><br>"); // '\uD83D\uDED2' is cart icon 🛒
                for (Product product : products) {
                    showProducts.append(product.getName())
                            .append(" - ₹").append(product.getPrice()).append("<br>");
                }
                System.out.println(showProducts);

                response =  showProducts.toString();
            }


        }

        session.sendMessage(new TextMessage(response));
    }

    private String isEcommerceQuery(String userInput) {

        String userInputWithPrompt = """
            This is user's original input: """ + userInput + """
            
            Your job is to check if the user’s input is anyhow related to my ecommerce application (products or the user’s own order items).
            
            Rules:
            1. If the input is NOT related to ecommerce, respond only with:
               No
            
            2. If the input IS related, generate a SQL query based on this schema:
               - product(id, category, description, image_url, name, price)
               - [categories of products available - Electronics, Clothing, Gadgets]
               - order_item(id, quantity, order_id, product_id)
            
            3. Query rules:
               - You can only access `product`, `order_item` tables.
               - Do not expose customer details.
               - User cannot insert, update, or delete any product or order_item data; the query should always be a DQL query (SELECT).
               - For order-related queries, always include a filter: `WHERE orders.user_id = <USER_ID>`.
               - Return only the SQL query, no explanations.
            
            Examples:
            
            User: Show all products under 12000
            Response:
            SELECT * FROM product WHERE price < 12000;
            
            User: Show mobiles under 50000
            Response:
            SELECT * FROM product WHERE category = 'mobile' AND price < 50000;
            
            User: Show my order items
            Response:
            SELECT p.*, oi.quantity
            FROM order_item oi
            JOIN orders o ON oi.order_id = o.id
            JOIN product p ON oi.product_id = p.id
            WHERE o.user_id = <USER_ID>;
            
            User: List all customers
            Response:
            No
            
            User: Hi, how are you?
            Response:
            No
            """;

//        System.out.println("new input with prompt "+userInputWithPrompt);

        String rowResponce = callGeminiForAnswer(userInputWithPrompt);

        String responce = extractGeminiText(rowResponce);

        System.out.println("gemini extracted responce " + responce);

        System.out.println("gemini response as sql query "+responce);
        return responce;

    }

    private String extractGeminiText(String responseJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseJson);

            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    return parts.get(0).path("text").asText();
                }
            }
            return "⚠️ No text found in response";
        } catch (Exception e) {
            e.printStackTrace();
            return "⚠️ Failed to parse Gemini response";
        }
    }


    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<Product> handleEcommerceQuery(String sqlQuery) {


        if (!sqlQuery.trim().toLowerCase().startsWith("select")) {
            throw new IllegalArgumentException("Only SELECT queries are allowed");
        }

        return jdbcTemplate.query(sqlQuery, (rs, rowNum) -> {
            Product product = new Product();
            product.setId(rs.getLong("id"));
            product.setCategory(rs.getString("category"));
            product.setDescription(rs.getString("description"));
            product.setName(rs.getString("name"));
            product.setPrice(rs.getDouble("price"));
            return product;
        });
    }


    private String callGeminiForAnswer(String userInput) {

        try {
            RestTemplate restTemplate = new RestTemplate();

            //  Wrap user input in a valid JSON format
            String jsonPayload = "{ \"contents\": [{ \"parts\": [{ \"text\": \"" + userInput + "\" }]}]}";

            //  Ensure headers are correct
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

            //  Correct API URL
            String url = GEMINI_URL + apiKey;

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

//            System.out.println(" Sent JSON to Gemini: " + jsonPayload);
            System.out.println(" Raw Response from Gemini: " + response.getBody());

            return response.getBody();
        }
        catch (Exception e) {
            System.err.println(" API Call Failed: " + e.getMessage());
            return "{\"error\":\"API call failed\"}";
        }


    }


}

