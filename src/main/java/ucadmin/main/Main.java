package ucadmin.main;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import ucadmin.discord.Discord;
import ucadmin.util.Logger;
import ucadmin.util.StartupManager;

import java.util.concurrent.CountDownLatch;

public class Main {
    public static void main(String[] args) {
        try {
            boolean testingArgApplied = applyRuntimeArgs(args);

            if (BotConfig.DISCORD_ENABLED) {
                JDABuilder builder = JDABuilder.createDefault(Security.TOKEN)
                        .enableIntents(GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                        .enableCache(CacheFlag.MEMBER_OVERRIDES);

                var jda = builder.build();
                Discord.bindJda(jda);

                jda.addEventListener(new StartupManager());
                jda.addEventListener(new ucadmin.commands.Ping());
                jda.addEventListener(new ucadmin.commands.ShutdownCommand());

                jda.updateCommands().addCommands(
                        Commands.slash("ping", "Replies with current latency and round-trip")
                ).queue();
                jda.updateCommands().addCommands(
                        Commands.slash("shutdown", "Shuts down the bot")
                ).queue();

                jda.awaitReady();
            } else {
                boolean started = StartupManager.startCore(null);
                if (!started) {
                    return;
                }
                Logger.log(Logger.TAG.SYSTEM, "Main: headless mode active; Discord startup skipped.");
            }

            if (testingArgApplied) {
                Logger.log(Logger.TAG.INFO, "Testing constant updated");
            }

            if (BotConfig.TESTING) {
                Logger.log(Logger.TAG.SYSTEM, "Main: entering TestingGrounds.");
                TestingGrounds.TestingGrounds();
                return;
            }

            if (!BotConfig.DISCORD_ENABLED) {
                keepHeadlessProcessAlive();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (BotConfig.DISCORD_ENABLED) {
                System.err.println("[0002] Main thread interrupted while waiting for startup readiness.");
            } else {
                System.err.println("[0029] Main headless keepalive interrupted.");
            }
        } catch (Throwable t) {
            System.err.println("[0001] Main bootstrap failed: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            t.printStackTrace();
        }
    }

    private static boolean applyRuntimeArgs(String[] args) {
        if (args == null || args.length == 0) return false;
        BotConfig.TESTING = Boolean.parseBoolean(args[0]);
        return true;
    }

    private static void keepHeadlessProcessAlive() throws InterruptedException {
        Logger.log(Logger.TAG.SYSTEM, "Main: headless core startup complete; keeping process alive without JDA.");
        new CountDownLatch(1).await();
    }
}
