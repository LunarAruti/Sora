package ucadmin.actions;

import net.dv8tion.jda.api.JDA;

/**
 * Shared JDA binding for actions.
 */
public final class Actions {

    private static volatile JDA jda;

    private Actions() {}

    /**
     * Binds a JDA instance for all actions to use.
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
