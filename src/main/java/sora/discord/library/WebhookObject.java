package sora.discord.library;

import sora.exceptions.ObjectException;
import sora.util.Logger;

/**
 * Webhook object.
 */
public class WebhookObject {
    private boolean sealed;
    private long webhookId;
    private long guildId;
    private long channelId;
    private long creatorId;
    private String name;
    private String avatar;
    private String token;
    private long applicationId;
    private String url;

    /**
     * Empty constructor for deserialization or manual field assignment via setters.
     */
    public WebhookObject() {}

    /**
     * Creates a webhook object with the core fields you can set at creation time.
     */
    public WebhookObject(long channelId, String name) {
        setChannelId(channelId);
        setName(name);
    }

    /** Read-only: webhook id (resolved). */
    public long getWebhookId() {
        return webhookId;
    }

    /** Internal: set webhook id (resolved). */
    void setWebhookId(long webhookId) {
        ensureMutable();
        if (webhookId < 0L) {
            throw new ObjectException("webhookId must be >= 0.", Logger.TAG.WARN);
        }
        this.webhookId = webhookId;
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

    /** Channel id for webhook creation or resolved webhook. */
    public long getChannelId() {
        return channelId;
    }

    /** Set channel id for webhook creation. */
    public void setChannelId(long channelId) {
        ensureMutable();
        if (channelId < 0L) {
            throw new ObjectException("channelId must be >= 0.", Logger.TAG.WARN);
        }
        this.channelId = channelId;
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

    /** Webhook name. */
    public String getName() {
        return name;
    }

    /** Set webhook name. */
    public void setName(String name) {
        ensureMutable();
        if (name == null || name.isBlank()) {
            throw new ObjectException("name must not be blank.", Logger.TAG.WARN);
        }
        if (name.length() > 80) {
            throw new ObjectException("name must be <= 80 characters.", Logger.TAG.WARN);
        }
        this.name = name;
    }

    /** Webhook avatar (URL or data). */
    public String getAvatar() {
        return avatar;
    }

    /** Set webhook avatar (URL or data). */
    public void setAvatar(String avatar) {
        ensureMutable();
        this.avatar = avatar;
    }

    /** Read-only: webhook token (resolved). */
    public String getToken() {
        return token;
    }

    /** Internal: set webhook token (resolved). */
    void setToken(String token) {
        ensureMutable();
        this.token = token;
    }

    /** Read-only: application id (resolved). */
    public long getApplicationId() {
        return applicationId;
    }

    /** Internal: set application id (resolved). */
    void setApplicationId(long applicationId) {
        ensureMutable();
        if (applicationId < 0L) {
            throw new ObjectException("applicationId must be >= 0.", Logger.TAG.WARN);
        }
        this.applicationId = applicationId;
    }

    /** Read-only: webhook URL (resolved). */
    public String getUrl() {
        return url;
    }

    /** Internal: set webhook URL (resolved). */
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
            throw new ObjectException("WebhookObject is sealed and cannot be modified.", Logger.TAG.OBJECT_REJECT);
        }
    }
}
