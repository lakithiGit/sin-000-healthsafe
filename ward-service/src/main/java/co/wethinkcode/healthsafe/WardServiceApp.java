package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Queue;
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TextMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WardServiceApp {

    private static final List<Ward> wards = new ArrayList<>();
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {

        loadWards();

        startStaffingEventConsumer();

        Javalin app = Javalin.create().start(7031);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/wards", ctx -> ctx.json(wards));

        app.get("/wards/{id}", ctx -> {

            String id = ctx.pathParam("id").toUpperCase();

            Ward ward = wards.stream()
                    .filter(w -> w.getWardId().equals(id))
                    .findFirst()
                    .orElse(null);

            if (ward == null) {
                ctx.status(404).result("Ward not found");
            } else {
                ctx.json(ward);
            }
        });

        app.post("/wards/{id}/equipment-failure", ctx -> {

            String wardId = ctx.pathParam("id").toUpperCase();

            Ward ward = wards.stream()
                    .filter(w -> w.getWardId().equals(wardId))
                    .findFirst()
                    .orElse(null);

            if (ward == null) {
                ctx.status(404).result("Ward not found");
                return;
            }

            publishEquipmentFailure(ward);

            ctx.status(202).result(
                    "Equipment failure alert published for " + wardId
            );
        });

        app.get("/departments", ctx -> {

            List<String> departments = wards.stream()
                    .map(Ward::getDepartment)
                    .filter(d -> d != null)
                    .distinct()
                    .collect(Collectors.toList());

            ctx.json(departments);
        });
    }

    private static void publishEquipmentFailure(Ward ward) {

    try {

        ActiveMQConnectionFactory factory =
                new ActiveMQConnectionFactory(MqConfig.BROKER_URL);

        try (Connection connection = factory.createConnection()) {

            connection.start();

            try (Session session =
                         connection.createSession(
                                 false,
                                 Session.AUTO_ACKNOWLEDGE)) {

                Queue queue =
                        session.createQueue(MqConfig.QUEUE);

                MessageProducer producer =
                        session.createProducer(queue);

                String messageText =
                        "Equipment failure detected in ward "
                                + ward.getWardId()
                                + " (" + ward.getDepartment() + ")";

                TextMessage message =
                        session.createTextMessage(messageText);

                // Persistent delivery means the broker stores
                // the message so it survives until delivered.
                message.setJMSDeliveryMode(
                        javax.jms.DeliveryMode.PERSISTENT
                );

                producer.send(message);

                producer.close();

                System.out.println(
                        "Published equipment failure: "
                                + messageText
                );
            }
        }}
    catch (Exception e) {

        System.err.println(
                "Failed to publish equipment failure: "
                        + e.getMessage()
            );
        }
    } 

    private static void loadWards() {

        try {

            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:7030/wards"))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            List<Ward> loadedWards = mapper.readValue(
                    response.body(),
                    new TypeReference<List<Ward>>() {}
            );

            wards.addAll(loadedWards);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void startStaffingEventConsumer() {

        Thread consumerThread = new Thread(() -> {

            try {

                ActiveMQConnectionFactory factory =
                        new ActiveMQConnectionFactory(MqConfig.BROKER_URL);

                Connection connection = factory.createConnection();

                connection.start();

                Session session =
                        connection.createSession(
                                false,
                                Session.AUTO_ACKNOWLEDGE
                        );

                Topic topic =
                        session.createTopic(MqConfig.TOPIC);

                MessageConsumer consumer =
                        session.createConsumer(topic);

                System.out.println(
                        "Ward Service subscribed to "
                                + MqConfig.TOPIC
                );

                while (true) {

                    Message message = consumer.receive();

                    if (message instanceof TextMessage) {

                        String json =
                                ((TextMessage) message).getText();

                        System.out.println(
                                "Received staffing event: "
                                        + json
                        );

                        handleStaffingEvent(json);
                    }
                }

            } catch (Exception e) {

                System.err.println(
                        "Staffing event consumer stopped: "
                                + e.getMessage()
                );
            }

        });

        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    private static void handleStaffingEvent(String json) {

        try {

            JsonStaffingEvent event =
                    mapper.readValue(json, JsonStaffingEvent.class);

            System.out.println(
                    "Ward " + event.getWardId()
                            + " staffing updated: "
                            + event.getSchedule()
            );

        } catch (Exception e) {

            System.err.println(
                    "Invalid staffing event: "
                            + e.getMessage()
            );
        }
    }

    public static class JsonStaffingEvent {

        private String wardId;
        private String department;
        private int alertLevel;
        private String schedule;

        public JsonStaffingEvent() {
        }

        public String getWardId() {
            return wardId;
        }

        public void setWardId(String wardId) {
            this.wardId = wardId;
        }

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public int getAlertLevel() {
            return alertLevel;
        }

        public void setAlertLevel(int alertLevel) {
            this.alertLevel = alertLevel;
        }

        public String getSchedule() {
            return schedule;
        }

        public void setSchedule(String schedule) {
            this.schedule = schedule;
        }
    }
}