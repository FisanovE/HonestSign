import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class CrptApi {

    private final TimeUnit timeUnit;
    private final int requestLimit;
    private LocalDateTime lastRequestTime = LocalDateTime.now();

    public static final int PORT = 8080;
    private final HttpServer server = HttpServer.create(new InetSocketAddress("localhost", PORT), 0);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String DATA_PATTERN = "yyyy-MM-dd";

    private final double timeRate;

    public CrptApi(TimeUnit timeUnit, int requestLimit) throws IOException {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.server.createContext("/", this::handleRequests);
        objectMapper.findAndRegisterModules();
        timeRate = calculateTimeRate(requestLimit, timeUnit);
    }

    private double calculateTimeRate(int requestLimit, TimeUnit timeUnit) {
        return TimeUnit.NANOSECONDS.convert(1, timeUnit) / requestLimit;
    }

    private synchronized double calculateCurrentTimeRate(LocalDateTime lastRequestTime) {
        return (double) Duration.between(lastRequestTime, LocalDateTime.now()).toNanos();
    }

    public void runServer() {
        server.setExecutor(null);
        server.start();
    }

    private synchronized void handleRequests(HttpExchange exchange) {
        if (timeRate < calculateCurrentTimeRate(lastRequestTime)) {
            lastRequestTime = LocalDateTime.now();

            String response;
            String body;

            try (exchange) {
                try {
                    String path = exchange.getRequestURI().getPath();
                    String httpMethod = exchange.getRequestMethod();
                    switch (httpMethod) {
                        case "POST":
                            InputStream inputStream;
                            if (Pattern.matches("^/api/v3/lk/documents/create", path)) {
                                inputStream = exchange.getRequestBody();
                                body = new String(inputStream.readAllBytes());

                                FullJson fullJson = objectMapper.readValue(body, FullJson.class);

                                Document document = fullJsonToDocument(fullJson);
                                createDocument(fullJson.description().participantInn(), document);

                                response = objectMapper.writeValueAsString(document);
                                sendText(exchange, response, 200);
                                return;
                            } else {
                                response = objectMapper.writeValueAsString(new ErrorMessage(
                                        LocalDateTime.now(),
                                        400,
                                        "Bad request",
                                        "Invalid path: " + path,
                                        path
                                ));

                                sendText(exchange, response, 400);
                                return;
                            }

                        default:
                            response = objectMapper.writeValueAsString(new ErrorMessage(
                                    LocalDateTime.now(),
                                    405,
                                    "Method Not Allowed",
                                    "Method Not Allowed: " + httpMethod,
                                    path
                            ));
                            sendText(exchange, response, 405);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            System.out.println("Log.INFO: Request limit exceeded");
            System.out.println("Log.INFO: The limit set is: " + requestLimit + " requests per " + timeUnit.toString().toLowerCase());
        }
    }

    private synchronized void sendText(HttpExchange exchange, String text, int httpCode) throws IOException {
        byte[] response = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(httpCode, 0);
        exchange.getResponseBody().write(response);
    }


    private synchronized Document createDocument(String signature, Document document) {
        System.out.println(signature);
        System.out.println(document);
        return document;
    }

    private synchronized Document fullJsonToDocument(FullJson fullJson) {
        return new Document(
                fullJson.docId,
                fullJson.docStatus,
                fullJson.importRequest,
                fullJson.ownerInn,
                fullJson.participantInn,
                fullJson.producerInn,
                fullJson.productionDate,
                fullJson.productionType,
                fullJson.regDate,
                fullJson.regNumber,
                fullJson.docType,
                fullJson.products
        );
    }

    record FullJson(
            @JsonProperty("doc_id") String docId,
            @JsonProperty("doc_status") String docStatus,
            boolean importRequest,
            @JsonProperty("owner_inn") String ownerInn,
            @JsonProperty("participant_inn") String participantInn,
            @JsonProperty("producer_inn") String producerInn,
            @JsonProperty("production_date") @JsonFormat(pattern = DATA_PATTERN) LocalDate productionDate,
            @JsonProperty("production_type") String productionType,
            @JsonProperty("reg_date") @JsonFormat(pattern = DATA_PATTERN) LocalDate regDate,
            @JsonProperty("reg_number") String regNumber,
            @JsonProperty("doc_type") CrptApi.DocumentType docType,
            Description description,
            List<Product> products
    ) {
    }

    record Document(
            @JsonProperty("doc_id") String docId,
            @JsonProperty("doc_status") String docStatus,
            boolean importRequest,
            @JsonProperty("owner_inn") String ownerInn,
            @JsonProperty("participant_inn") String participantInn,
            @JsonProperty("producer_inn") String producerInn,
            @JsonProperty("production_date") @JsonFormat(pattern = DATA_PATTERN) LocalDate productionDate,
            @JsonProperty("production_type") String productionType,
            @JsonProperty("reg_date") @JsonFormat(pattern = DATA_PATTERN) LocalDate regDate,
            @JsonProperty("reg_number") String regNumber,
            @JsonProperty("doc_type") CrptApi.DocumentType docType,
            List<Product> products
    ) {
    }

    record Product(
            @JsonProperty("certificate_document") String certificateDocument,
            @JsonProperty("certificate_document_date") @JsonFormat(pattern = DATA_PATTERN) LocalDate certificateDocumentDate,
            @JsonProperty("certificate_document_number") String certificateDocumentNumber,
            @JsonProperty("owner_inn") String ownerInn,
            @JsonProperty("producer_inn") String producerInn,
            @JsonProperty("production_date") @JsonFormat(pattern = DATA_PATTERN) LocalDate productionDate,
            @JsonProperty("tnved_code") String tnvedCode,
            @JsonProperty("uit_code") String uitCode,
            @JsonProperty("uitu_code") String uituCode
    ) {
    }

    record Description(String participantInn) {
    }

    record ErrorMessage(
            LocalDateTime timestamp,
            int status,
            String error,
            String message,
            String path
    ) {
    }

    enum DocumentType {
        LP_INTRODUCE_GOODS
    }
}
