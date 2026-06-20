package sora.discord.library;

import java.util.List;

import sora.exceptions.ObjectException;
import sora.util.Logger;

/**
 * Emoji object.
 */
public class EmojiObject {
    private boolean sealed;
    private long emojiId;
    private long guildId;
    private String name;
    private boolean animated;
    private boolean available;
    private boolean managed;
    private List<Long> roleIds;
    private long creatorId;
    private String url;

    /**
     * Empty constructor for deserialization.
     */
    public EmojiObject() {}

    /** Read-only: emoji id (resolved). */
    public long getEmojiId() {
        return emojiId;
    }

    /** Internal: set emoji id (resolved). 0 when unicode emoji has no id. */
    void setEmojiId(long emojiId) {
        ensureMutable();
        if (emojiId < 0L) {
            throw new ObjectException("emojiId must be >= 0.", Logger.TAG.WARN);
        }
        this.emojiId = emojiId;
    }

    /** Read-only: guild id this emoji belongs to (resolved). */
    public long getGuildId() {
        return guildId;
    }

    /** Internal: set guild id (resolved). */
    void setGuildId(long guildId) {
        ensureMutable();
        if (guildId < 0L) {
            throw new ObjectException("guildId must be >= 0.", Logger.TAG.WARN);
        }
        this.guildId = guildId;
    }

    /** Read-only: emoji name (resolved, custom or unicode). */
    public String getName() {
        return name;
    }

    /** Internal: set emoji name (resolved). */
    void setName(String name) {
        ensureMutable();
        if (name == null || name.isBlank()) {
            throw new ObjectException("name must not be blank.", Logger.TAG.WARN);
        }
        this.name = name;
    }

    /** Read-only: whether the emoji is animated (resolved). */
    public boolean isAnimated() {
        return animated;
    }

    /** Internal: set animated flag (resolved). */
    void setAnimated(boolean animated) {
        ensureMutable();
        this.animated = animated;
    }

    /** Read-only: whether the emoji is available (resolved). */
    public boolean isAvailable() {
        return available;
    }

    /** Internal: set available flag (resolved). */
    void setAvailable(boolean available) {
        ensureMutable();
        this.available = available;
    }

    /** Read-only: whether the emoji is managed (resolved). */
    public boolean isManaged() {
        return managed;
    }

    /** Internal: set managed flag (resolved). */
    void setManaged(boolean managed) {
        ensureMutable();
        this.managed = managed;
    }

    /** Read-only: role ids that can use the emoji (resolved). */
    public List<Long> getRoleIds() {
        return roleIds;
    }

    /** Internal: set role ids (resolved). */
    void setRoleIds(List<Long> roleIds) {
        ensureMutable();
        this.roleIds = roleIds;
    }

    /** Read-only: creator id (resolved). */
    public long getCreatorId() {
        return creatorId;
    }

    /** Internal: set creator id (resolved). */
    void setCreatorId(long creatorId) {
        ensureMutable();
        if (creatorId < 0L) {
            throw new ObjectException("creatorId must be >= 0.", Logger.TAG.WARN);
        }
        this.creatorId = creatorId;
    }

    /** Read-only: emoji URL (resolved, may be blank for unicode). */
    public String getUrl() {
        return url;
    }

    /** Internal: set emoji URL (resolved). */
    void setUrl(String url) {
        ensureMutable();
        this.url = url;
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
            throw new ObjectException("EmojiObject is sealed and cannot be modified.", Logger.TAG.OBJECT_REJECT);
        }
    }
}
