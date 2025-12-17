package ucadmin.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ucadmin.database.BatchManager;
import ucadmin.main.BotConfig;
import ucadmin.network.NetworkManager;
import ucadmin.util.Logger;
import ucadmin.util.Logger.TAG;
import ucadmin.database.QueueManager;

/**
 * Handles the /shutdown command.
 *
 * Gracefully terminates all background processes, flushes queued writes,
 * closes the logger, and exits the application.
 */
public class ShutdownCommand extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("shutdown")) return;

        // Confirm only the bot owner or admins can run this
        if (!event.getUser().getId().equals(BotConfig.OWNER_ID)) {
            event.reply("You are not authorized to shut down this bot.").setEphemeral(true).queue();
            Logger.log(TAG.WARN, "Unauthorized shutdown attempt by " + event.getUser().getAsTag());
            return;
        }

        event.reply("Shutting down bot... please wait.").queue();

        try {
            Logger.log(TAG.SYSTEM, "Shutdown command received. Beginning graceful termination...");

            ucadmin.network.NetworkManager.shutdown();
            ucadmin.database.BatchManager.shutdown(); // this flushes QueueManager

            Logger.log(TAG.INFO, "Graceful shutdown complete. Exiting process.");

            Thread.sleep(2000);
            event.getJDA().shutdown();
            Thread.sleep(2000);

            Logger.log(TAG.SYSTEM, "System exiting cleanly.");
            System.exit(0);

        } catch (Exception e) {
            Logger.log(TAG.ERROR, "Shutdown failed: " + e.getMessage());
            event.getHook().sendMessage("Shutdown encountered an error: " + e.getMessage()).queue();
        }
    }
}
