package ucadmin.actions;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Basic message actions.
 */
public final class Messages {

    private Messages() {}

    private static final long EPHEMERAL_DELETE_DELAY_SECONDS = 15;

    /**
     * Sends a plain text message and returns the message id.
     */
    public static CompletableFuture<String> sendMessage(String channelId, String content) {
        return sendMessage(null, channelId, content, false, false);
    }

    /**
     * Sends a message with basic routing options and returns the message id.
     */
    public static CompletableFuture<String> sendMessage(
            String serverId,
            String channelId,
            String content,
            boolean reply,
            boolean ephemeral
    ) {
        if (channelId == null || channelId.isBlank() || content == null) {
            return CompletableFuture.completedFuture(null);
        }

        MessageChannel channel = Channels.resolveMessageChannel(serverId, channelId);
        if (channel == null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Message> sendFuture;
        if (reply) {
            sendFuture = channel.getHistory().retrievePast(1).submit()
                    .thenCompose(history -> {
                        if (history.isEmpty()) {
                            return channel.sendMessage(content).submit();
                        }
                        return history.get(0).reply(content).submit();
                    });
        } else {
            sendFuture = channel.sendMessage(content).submit();
        }

        if (ephemeral) {
            sendFuture.thenAccept(message ->
                    message.delete().queueAfter(EPHEMERAL_DELETE_DELAY_SECONDS, TimeUnit.SECONDS));
        }

        return sendFuture.thenApply(Message::getId);
    }
}
