package co.wethinkcode.healthsafe;

import com.opencsv.CSVReader;
import io.javalin.Javalin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class IngestionServiceApp {

    private static final List<Ward> wards = new ArrayList<>();

    public static void main(String[] args) {

        loadWards();

        Javalin app = Javalin.create().start(7030);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/wards", ctx -> ctx.json(wards));
    }

    private static void loadWards() {

        try {
            InputStream input = IngestionServiceApp.class
                    .getClassLoader()
                    .getResourceAsStream("wards-outdated.csv");

            CSVReader reader = new CSVReader(new InputStreamReader(input));

            reader.readNext(); // skip header

            String[] row;

            while ((row = reader.readNext()) != null) {

                String wardId = cleanId(row[0]);
                String wing = cleanName(row[1]);
                String department = cleanDepartment(row[2]);
                Integer beds = cleanBeds(row[3]);

                addOrUpdateWard(new Ward(
                    wardId,
                    wing,
                    department,
                    beds
                ));
            }

            reader.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String cleanId(String value) {
        return value.trim().toUpperCase();
    }

    private static String cleanName(String value) {

        value = value.trim();
        value = value.replaceAll("\\s+", " ");

        if (value.isEmpty()) {
            return null;
        }

        String[] words = value.toLowerCase().split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
        }

        return result.toString().trim();
    }

    private static String cleanDepartment(String value) {

        String cleaned = cleanName(value);

        if ("Pediatrics".equals(cleaned)) {
            return "Paediatrics";
        }

        if ("Icu".equals(cleaned)) {
            return "ICU";
        }

        return cleaned;
    }

    private static Integer cleanBeds(String value) {

        try {
            int beds = Integer.parseInt(value.trim());

            if (beds < 0 || beds > 100) {
                return null;
            }

            return beds;

        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private static void addOrUpdateWard(Ward newWard) {
            for (int i = 0; i < wards.size(); i++) {

                Ward existingWard = wards.get(i);

                if (existingWard.getWardId().equals(newWard.getWardId())) {

                    if (existingWard.getBedsAvailable() == null
                        && newWard.getBedsAvailable() != null) {

                    wards.set(i, newWard);
                }

                return;
            }
        }

        wards.add(newWard);
    }
}
