package sora.discord.library;

import sora.exceptions.ObjectException;
import sora.util.Logger;

/**
 * Sticker object.
 */
public class StickerObject {
    private boolean sealed;
    private long stickerId;
    private long guildId;
    private String name;
    private String description;
    private boolean available;
    private long creatorId;
    private String url;

    /**
     * Empty constructor for deserialization.
     */
    public StickerObject() {}

    /** Read-only: sticker id (resolved). */
    public long getStickerId() {
        return stickerId;
    }

    /** Internal: set sticker id (resolved). */
    void setStickerId(long stickerId) {
        ensureMutable();
        if (stickerId < 0L) {
            throw new ObjectException("stickerId must be >= 0.", Logger.TAG.WARN);
        }
        this.stickerId = stickerId;
    }

    /** Read-only: guild id (resolved). */
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

    /** Read-only: sticker name (resolved). */
    public String getName() {
        return name;
    }

    /** Internal: set sticker name (resolved). */
    void setName(String name) {
        ensureMutable();
        if (name == null || name.isBlank()) {
            throw new ObjectException("name must not be blank.", Logger.TAG.WARN);
        }
        this.name = name;
    }

    /** Read-only: sticker description (resolved). */
    public String getDescription() {
        return description;
    }

    /** Internal: set sticker description (resolved). */
    void setDescription(String description) {
        ensureMutable();
        this.description = description;
    }

    /** Read-only: whether sticker is available (resolved). */
    public boolean isAvailable() {
        return available;
    }

    /** Internal: set available flag (resolved). */
    void setAvailable(boolean available) {
        ensureMutable();
        this.available = available;
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

    /** Read-only: sticker URL (resolved). */
    public String getUrl() {
        return url;
    }

    /** Internal: set sticker URL (resolved). */
    void setUrl(String url) {
        ensureMutable();
        if (url == null || url.isBlank()) {
            throw new ObjectException("url must not be blank.", Logger.TAG.WARN);
        }
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
            throw new ObjectException("StickerObject is sealed and cannot be modified.", Logger.TAG.OBJECT_REJECT);
        }
    }
}
