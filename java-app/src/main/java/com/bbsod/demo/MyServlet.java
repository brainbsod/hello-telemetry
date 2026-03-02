package com.bbsod.demo;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

// OpenTelemetry SDK
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
// OpenTelemetry API
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;

public class MyServlet extends HttpServlet {

    // Define Class Fields
    private static final String INSTRUMENTATION_NAME = MyServlet.class.getName();
    private final Meter meter;
    private final LongCounter requestCounter;

    // Constructor
    public MyServlet() {
        OpenTelemetry openTelemetry = initOpenTelemetry();
        this.meter = openTelemetry.getMeter(INSTRUMENTATION_NAME);
        this.requestCounter = meter.counterBuilder("app.db.db_requests")
                .setDescription("Count DB requests")
                .build();
    }

    static OpenTelemetry initOpenTelemetry() {

        // Set up the resource with service.name
        Resource resource = Resource.create(Attributes.of(AttributeKey.stringKey("service.name"),
                "tomcat-service"));

        // Metrics
        OtlpGrpcMetricExporter otlpGrpcMetricExporter = OtlpGrpcMetricExporter.builder()
                .setEndpoint("http://ht-otel-collector:4317")
                .build();

        PeriodicMetricReader periodicMetricReader = PeriodicMetricReader.builder(otlpGrpcMetricExporter)
                .setInterval(java.time.Duration.ofSeconds(20))
                .build();

        SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(periodicMetricReader)
                .build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setMeterProvider(sdkMeterProvider)
                .build();

        // Cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));

        return sdk;

    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        List<JSONObject> dataList = new ArrayList<>();
        PrintWriter out = response.getWriter();
        response.setContentType("text/html");

        // Sleep for 2 seconds
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Establish database connection and get data
        requestCounter.add(1);

        // JDBC connection parameters
        String jdbcUrl = "jdbc:mysql://ht-mysql:3306/mydatabase";
        String jdbcUser = "myuser";
        String jdbcPassword = "mypassword";

        try {
            // Load MySQL JDBC Driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Establish connection
            Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser,
                    jdbcPassword);

            // Create a statement
            Statement statement = connection.createStatement();

            // Execute a query
            String query = "SELECT * FROM mytable";
            ResultSet resultSet = statement.executeQuery(query);

            // Build web page
            out.println("<html><body>");
            out.println("<h1>Database Results</h1>");
            out.println("<table border='1'>");
            out.println("<tr><th>ID</th><th>Name</th><th>Age</th></tr>");

            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String name = resultSet.getString("name");
                int age = resultSet.getInt("age");
                out.println("<tr><td>" + id + "</td><td>" + name + "</td><td>" + age
                        + "</td></tr>");

                JSONObject dataObject = new JSONObject();
                dataObject.put("id", id);
                dataObject.put("name", name);
                dataObject.put("age", age);
                dataList.add(dataObject);

            }
            out.println("</table>");
        } catch (ClassNotFoundException e) {
            System.out.println("MySQL JDBC Driver not found.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.out.println("Connection failed.");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            out.println("<h2>Error: " + e.getMessage() + "</h2>");
        }

        // Make a request to the Python microservice
        String averageAge = getAverageAge(dataList);
        out.println("<h2>Average Age: " + averageAge + "</h2>");
        out.println("</body></html>");

    }

    private String getAverageAge(List<JSONObject> dataList) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost("http://ht-python-service:5000/compute_average_age");
            httpPost.setHeader("Content-Type", "application/json");

            JSONObject requestData = new JSONObject();
            requestData.put("data", new JSONArray(dataList));

            StringEntity entity = new StringEntity(requestData.toString());
            httpPost.setEntity(entity);

            String responseString = httpClient.execute(httpPost, response ->
                    EntityUtils.toString(response.getEntity()));
            JSONObject responseJson = new JSONObject(responseString);
            return responseJson.get("average_age").toString();
        }
    }
}