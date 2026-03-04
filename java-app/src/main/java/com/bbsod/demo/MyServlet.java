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
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.bridge.SLF4JBridgeHandler;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

// OpenTelemetry SDK

import io.opentelemetry.api.GlobalOpenTelemetry;
// OpenTelemetry API
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

public class MyServlet extends HttpServlet {

    // Define Class Fields
    private static final String INSTRUMENTATION_NAME = MyServlet.class.getName();
    private final Meter meter;
    private final LongCounter requestCounter;
    private final Tracer tracer;
    private static final java.util.logging.Logger julLogger = Logger.getLogger("jul-logger");

    // Constructor
    public MyServlet() {
        OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
        this.meter = openTelemetry.getMeter(INSTRUMENTATION_NAME);
        this.requestCounter = meter.counterBuilder("app.db.db_requests")
                .setDescription("Count DB requests")
                .build();
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);

        // Install OpenTelemetry in logback appender
        OpenTelemetryAppender.install(openTelemetry);

        // Install SLF4JBridgeHandler to route JUL logs to SLF4J
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        List<JSONObject> dataList = new ArrayList<>();
        PrintWriter out = response.getWriter();
        response.setContentType("text/html");

        // Sleep for 2 seconds
        // Span to capture sleep
        // parentContext = Context.current().with(parentSpan);
        Span sleepSpan = tracer.spanBuilder("SleepForTwoSeconds")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            sleepSpan.end();
        }

        // Establish database connection and get data
        requestCounter.add(1);

        // Start Database Span
        // Context parentContext = Context.current().with(sleepSpan);
        Span dbSpan = tracer.spanBuilder("DatabaseConnection")
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();

        // JDBC connection parameters
        String jdbcUrl = "jdbc:mysql://ht-mysql:3306/mydatabase";
        String jdbcUser = "myuser";
        String jdbcPassword = "mypassword";

        try {
            julLogger.info("DB Connection initiated");
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
        } finally {
            dbSpan.end();
        }

        // Make a request to the Python microservice
        String averageAge = getAverageAge(dataList);
        out.println("<h2>Average Age: " + averageAge + "</h2>");
        out.println("</body></html>");

    }

    private String getAverageAge(List<JSONObject> dataList) throws IOException {

        Span computeSpan = tracer.spanBuilder("Compute Request")
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();

        // Create Baggage
        Baggage baggage = Baggage.builder()
                .put("user.id", "12345")
                .put("user.name", "john")
                .build();

        Context contextWithBaggage = Context.current().with(baggage);

        try (Scope scope = computeSpan.makeCurrent(); CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost("http://ht-python-service:5000/compute_average_age");
            httpPost.setHeader("Content-Type", "application/json");

            JSONObject requestData = new JSONObject();
            requestData.put("data", new JSONArray(dataList));

            StringEntity entity = new StringEntity(requestData.toString());
            httpPost.setEntity(entity);

            // Inject the context into the HTTP request headers using
            // W3CTraceContextPropagator
            // W3CTraceContextPropagator propagator =
            // W3CTraceContextPropagator.getInstance();
            // propagator.inject(contextWithBaggage, httpPost, HttpPost::setHeader);

            W3CBaggagePropagator.getInstance().inject(contextWithBaggage, httpPost, HttpPost::setHeader);

            String responseString = httpClient.execute(httpPost, response ->
                    EntityUtils.toString(response.getEntity()));
            JSONObject responseJson = new JSONObject(responseString);
            return responseJson.get("average_age").toString();
        } finally {
            computeSpan.end();
        }
    }
}