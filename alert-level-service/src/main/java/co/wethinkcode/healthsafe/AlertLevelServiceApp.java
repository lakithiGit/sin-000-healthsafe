package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

public class AlertLevelServiceApp {

    private static int currentLevel = 0;

    public static void main(String[] args) {

        Javalin app = Javalin.create().start(7032);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/alert-level", ctx -> {
            ctx.json(new AlertLevel(currentLevel));
        });

        app.post("/alert-level/{level}", ctx -> {

            int level = Integer.parseInt(ctx.pathParam("level"));

            if (level < 0 || level > 8) {
                ctx.status(400).result("Alert level must be between 0 and 8");
                return;
            }

            currentLevel = level;

            ctx.json(new AlertLevel(currentLevel));
        });
    }

    public static class AlertLevel {

        private final int level;

        public AlertLevel(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }
}