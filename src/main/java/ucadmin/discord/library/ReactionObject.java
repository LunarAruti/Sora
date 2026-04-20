package ucadmin.discord.library;

import ucadmin.exceptions.ObjectException;
import ucadmin.util.Logger;

/**
 * Reaction object (emoji + user id).
 */
public class ReactionObject {
    private boolean sealed;
    private EmojiObject emoji;
    private long userId;

    /**
     * Empty constructor for deserialization.
     */
    public ReactionObject() {}

    /** Read-only: emoji used in this reaction (resolved). */
    public EmojiObject getEmoji() {
        return emoji;
    }

    /** Internal: set emoji (resolved). */
    void setEmoji(EmojiObject emoji) {
        ensureMutable();
        if (emoji == null) {
            throw new ObjectException("emoji must not be null.", Logger.TAG.WARN);
        }
        this.emoji = emoji;
    }

    /** Read-only: user id who reacted (resolved). */
    public long getUserId() {
        return userId;
    }

    /** Internal: set user id (resolved). */
    void setUserId(long userId) {
        ensureMutable();
        if (userId < 0L) {
            throw new ObjectException("userId must be >= 0.", Logger.TAG.WARN);
        }
        this.userId = userId;
    }

    /** Whether this object is sealed (read-only). */
    public boolean isSealed() {
        return sealed;
    }

    /** Internal: sets sealed state to prevent or allow mutation. */
    void setSealed(boolean sealed) {
        this.sealed = sealed;
    }

    /** Internal: seals the object to prevent further mutation. */
    void seal() {
        setSealed(true);
    }

    private void ensureMutable() {
        if (sealed) {
            throw new ObjectException("ReactionObject is sealed and cannot be modified.", Logger.TAG.OBJECT_REJECT);
        }
    }
}
