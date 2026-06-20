package sora.discord;

import net.dv8tion.jda.api.JDA;

/**
 * Shared JDA binding for Discord actions/objects.
 */
public final class Discord {

    private static volatile JDA jda;

    private Discord() {}

    /**
     * Binds a JDA instance for Discord actions to use.
     */
    public static void bindJda(JDA instance) {
        jda = instance;
    }

    /**
     * Returns the bound JDA instance (may be null if not bound).
     */
    public static JDA getJda() {
        return jda;
    }
}
