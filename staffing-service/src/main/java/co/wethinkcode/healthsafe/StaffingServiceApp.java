package co.wethinkcode.healthsafe;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.wethinkcode.healthsafe.mq.MqConfig;
import io.javalin.Javalin;

public class StaffingServiceApp {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {

        Javalin app = Javalin.create().start(7033);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/staffing/{wardId}", ctx -> {

            String wardId = ctx.pathParam("wardId").toUpperCase();

            try {

                // Ask Ward Service if the ward exists
                HttpRequest wardRequest = HttpRequest.newBuilder()
                        .uri(URI.create(
                                "http://localhost:7031/wards/" + wardId
                        ))
                        .GET()
                        .build();

                HttpResponse<String> wardResponse =
                        client.send(
                                wardRequest,
                                HttpResponse.BodyHandlers.ofString()
                        );

                if (wardResponse.statusCode() == 404) {
                    ctx.status(404).result("Ward not found");
                    return;
                }

                if (wardResponse.statusCode() != 200) {
                    ctx.status(502).result("Ward service unavailable");
                    return;
                }

                JsonNode ward =
                        mapper.readTree(wardResponse.body());

                // Ask Alert Level Service for current emergency level
                HttpRequest alertRequest = HttpRequest.newBuilder()
                        .uri(URI.create(
                                "http://localhost:7032/alert-level"
                        ))
                        .GET()
                        .build();

                HttpResponse<String> alertResponse =
                        client.send(
                                alertRequest,
                                HttpResponse.BodyHandlers.ofString()
                        );

                if (alertResponse.statusCode() != 200) {
                    ctx.status(502).result(
                            "Alert level service unavailable"
                    );
                    return;
                }

                JsonNode alert =
                        mapper.readTree(alertResponse.body());

                int level = alert.get("level").asInt();

                String department =
                        ward.get("department").asText();

                String schedule;

                if (level >= 7) {
                    schedule = "Emergency on-call team";
                } else if (level >= 4) {
                    schedule = "Extended on-call team";
                } else {
                    schedule = "Normal on-call team";
                }

                StaffingResponse response =
                        new StaffingResponse(
                                wardId,
                                department,
                                level,
                                schedule
                        );

                // Publish staffing event to ActiveMQ topic
                publishStaffingEvent(response);

                ctx.json(response);

            } catch (Exception e) {

                e.printStackTrace();

                ctx.status(502).result(
                        "Unable to contact required services"
                );
            }
        });
    }

    private static void publishStaffingEvent(StaffingResponse response) {

        try {

            ActiveMQConnectionFactory factory =
                    new ActiveMQConnectionFactory(MqConfig.BROKER_URL);

            try (Connection connection = factory.createConnection()) {

                connection.start();

                try (Session session =
                             connection.createSession(
                                     false,
                                     Session.AUTO_ACKNOWLEDGE)) {

                    Topic topic =
                            session.createTopic(MqConfig.TOPIC);

                    MessageProducer producer =
                            session.createProducer(topic);

                    String json =
                            mapper.writeValueAsString(response);

                    TextMessage message =
                            session.createTextMessage(json);

                    producer.send(message);

                    producer.close();
                }
            }

        } catch (Exception e) {

            System.err.println(
                    "Failed to publish staffing event: "
                            + e.getMessage()
            );
        }
    }

    public static class StaffingResponse {

        private final String wardId;
        private final String department;
        private final int alertLevel;
        private final String schedule;

        public StaffingResponse(
                String wardId,
                String department,
                int alertLevel,
                String schedule) {

            this.wardId = wardId;
            this.department = department;
            this.alertLevel = alertLevel;
            this.schedule = schedule;
        }

        public String getWardId() {
            return wardId;
        }

        public String getDepartment() {
            return department;
        }

        public int getAlertLevel() {
            return alertLevel;
        }

        public String getSchedule() {
            return schedule;
        }
    }
}