package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import io.javalin.Javalin;
import org.apache.activemq.ActiveMQConnectionFactory;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

public class EquipmentAlertServiceApp {

    public static void main(String[] args) {

        startQueueConsumer();

        Javalin app = Javalin.create().start(7034);

        app.get("/health", ctx -> ctx.result("OK"));
    }

    private static void startQueueConsumer() {

        Thread consumerThread = new Thread(() -> {

            try {

                ActiveMQConnectionFactory factory =
                        new ActiveMQConnectionFactory(MqConfig.BROKER_URL);

                Connection connection = factory.createConnection();
                connection.start();

                Session session =
                        connection.createSession(
                                false,
                                Session.CLIENT_ACKNOWLEDGE);

                Queue queue =
                        session.createQueue(MqConfig.QUEUE);

                MessageConsumer consumer =
                        session.createConsumer(queue);

                System.out.println(
                        "Equipment alert consumer listening on "
                                + MqConfig.QUEUE
                );

                while (true) {

                    Message message = consumer.receive();

                    if (message instanceof TextMessage) {

                        String text =
                                ((TextMessage) message).getText();

                        System.out.println(
                                "Received equipment failure alert: "
                                        + text
                        );

                        // Only acknowledge after successful processing.
                        message.acknowledge();

                        System.out.println(
                                "Equipment failure alert acknowledged"
                        );
                    }
                }

            } catch (Exception e) {

                System.err.println(
                        "Equipment alert consumer failed: "
                                + e.getMessage()
                );
            }

        });

        consumerThread.start();
    }
}