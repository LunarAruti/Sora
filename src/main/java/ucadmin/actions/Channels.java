package ucadmin.actions;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * Channel lookup helpers.
 */
public final class Channels {

    private Channels() {}

    /**
     * Resolves a message channel by id using the bound JDA instance.
     */
    public static MessageChannel resolveMessageChannel(String channelId) {
        return resolveMessageChannel(null, channelId);
    }

    /**
     * Resolves a message channel by server and channel id using the bound JDA instance.
     */
    public static MessageChannel resolveMessageChannel(String serverId, String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return null;
        }

        JDA jda = Actions.getJda();
        if (jda == null) {
            return null;
        }

        if (serverId != null && !serverId.isBlank()) {
            var guild = jda.getGuildById(serverId);
            if (guild != null) {
                var fromGuild = guild.getChannelById(GuildMessageChannel.class, channelId);
                if (fromGuild != null) {
                    return fromGuild;
                }
            }
        }

        return jda.getChannelById(MessageChannel.class, channelId);
    }
}
