package ucadmin.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import ucadmin.main.BotConfig;
import ucadmin.util.Logger;
import ucadmin.util.Logger.TAG;
import ucadmin.util.ShutdownManager;

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
            ShutdownManager.shutdown(event.getJDA());

        } catch (Exception e) {
            Logger.log(TAG.ERROR, "Shutdown failed: " + e.getMessage());
            event.getHook().sendMessage("Shutdown encountered an error: " + e.getMessage()).queue();
        }
    }
}
