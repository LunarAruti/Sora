package ucadmin.main;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import ucadmin.util.Logger;
import ucadmin.util.StartupManager;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        JDABuilder builder = JDABuilder.createDefault(Security.TOKEN)
                .enableIntents(GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                .enableCache(CacheFlag.MEMBER_OVERRIDES);

        var jda = builder.build();

        // Register listeners
        jda.addEventListener(new ucadmin.commands.Ping());
        jda.addEventListener(new ucadmin.commands.ShutdownCommand());
        jda.addEventListener(new StartupManager());

        // Slash commands
        jda.updateCommands().addCommands(
                Commands.slash("ping", "Replies with current latency and round-trip")
        ).queue();
        jda.updateCommands().addCommands(
                Commands.slash("shutdown", "Shuts down the bot")
        ).queue();

        // Block until READY event fired (StartupManager.onReady has run)
        jda.awaitReady();

        //Testing grounds
        if (args.length > 0) {
            BotConfig.TESTING = Boolean.parseBoolean(args[0]);
            Logger.log(Logger.TAG.INFO, "Testing constant updated");
        }

        if (BotConfig.TESTING) {
            TestingGrounds.TestingGrounds();
        }
    }
}
