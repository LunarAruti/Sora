package sora.commands;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public class Ping extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("ping")) return;

        long startTime = System.currentTimeMillis();
        JDA jda = event.getJDA();
        long gatewayPing = jda.getGatewayPing();

        event.reply("Pinging...").queue(interactionHook -> {
            long endTime = System.currentTimeMillis();
            long roundTrip = endTime - startTime;

            interactionHook.editOriginal(
                    String.format("Gateway latency: `%dms`\n" +
                                    "Round-trip latency: `%dms`",
                            gatewayPing, roundTrip)
            ).queue();
        });
    }
}
