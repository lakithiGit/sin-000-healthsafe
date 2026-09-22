package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WardServiceApp {

    private static final List<Ward> wards = new ArrayList<>();

    public static void main(String[] args) {

        loadWards();

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

        app.get("/departments", ctx -> {

            List<String> departments = wards.stream()
                    .map(Ward::getDepartment)
                    .filter(d -> d != null)
                    .distinct()
                    .collect(Collectors.toList());

            ctx.json(departments);
        });
    }

    private static void loadWards() {

        try {

            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:7030/wards"))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();

            List<Ward> loadedWards = mapper.readValue(
                    response.body(),
                    new TypeReference<List<Ward>>() {}
            );

            wards.addAll(loadedWards);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
