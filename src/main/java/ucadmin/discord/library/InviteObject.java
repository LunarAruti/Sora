package ucadmin.discord.library;

import ucadmin.exceptions.ObjectException;
import ucadmin.util.Logger;

/**
 * Invite object.
 */
public class InviteObject {
    private boolean sealed;
    private String code;
    private long guildId;
    private long channelId;
    private long inviterId;
    private int uses;
    private int maxUses;
    private int maxAge;
    private boolean temporary;
    private long createdAt;
    private String url;
    private String guildName;
    private String channelName;

    /**
     * Empty constructor for deserialization.
     */
    public InviteObject() {}

    /** Read-only: invite code (resolved). */
    public String getCode() {
        return code;
    }

    /** Internal: set invite code (resolved). */
    void setCode(String code) {
        ensureMutable();
        if (code == null || code.isBlank()) {
            throw new ObjectException("code must not be blank.", Logger.TAG.WARN);
        }
        this.code = code;
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

    /** Channel id for invite creation or resolved invite. */
    public long getChannelId() {
        return channelId;
    }

    /** Set channel id for invite creation. */
    public void setChannelId(long channelId) {
        ensureMutable();
        if (channelId < 0L) {
            throw new ObjectException("channelId must be >= 0.", Logger.TAG.WARN);
        }
        this.channelId = channelId;
    }

    /** Read-only: inviter id (resolved). */
    public long getInviterId() {
        return inviterId;
    }

    /** Internal: set inviter id (resolved). */
    void setInviterId(long inviterId) {
        ensureMutable();
        if (inviterId < 0L) {
            throw new ObjectException("inviterId must be >= 0.", Logger.TAG.WARN);
        }
        this.inviterId = inviterId;
    }

    /** Read-only: invite uses (resolved). */
    public int getUses() {
        return uses;
    }

    /** Internal: set invite uses (resolved). */
    void setUses(int uses) {
        ensureMutable();
        if (uses < 0) {
            throw new ObjectException("uses must be >= 0.", Logger.TAG.WARN);
        }
        this.uses = uses;
    }

    /** Max uses before the invite expires (0 = unlimited). */
    public int getMaxUses() {
        return maxUses;
    }

    /** Set max uses before the invite expires (0 = unlimited). */
    public void setMaxUses(int maxUses) {
        ensureMutable();
        if (maxUses < 0) {
            throw new ObjectException("maxUses must be >= 0.", Logger.TAG.WARN);
        }
        this.maxUses = maxUses;
    }

    /** Max age of invite in seconds (0 = unlimited). */
    public int getMaxAge() {
        return maxAge;
    }

    /** Set max age of invite in seconds (0 = unlimited). */
    public void setMaxAge(int maxAge) {
        ensureMutable();
        if (maxAge < 0) {
            throw new ObjectException("maxAge must be >= 0.", Logger.TAG.WARN);
        }
        this.maxAge = maxAge;
    }

    /** Whether the invite grants temporary membership. */
    public boolean isTemporary() {
        return temporary;
    }

    /** Set whether the invite grants temporary membership. */
    public void setTemporary(boolean temporary) {
        ensureMutable();
        this.temporary = temporary;
    }

    /** Read-only: invite creation timestamp (resolved). */
    public long getCreatedAt() {
        return createdAt;
    }

    /** Internal: set invite creation timestamp (resolved). */
    void setCreatedAt(long createdAt) {
        ensureMutable();
        if (createdAt < 0L) {
            throw new ObjectException("createdAt must be >= 0.", Logger.TAG.WARN);
        }
        this.createdAt = createdAt;
    }

    /** Read-only: invite URL (resolved). */
    public String getUrl() {
        return url;
    }

    /** Internal: set invite URL (resolved). */
    void setUrl(String url) {
        ensureMutable();
        if (url == null || url.isBlank()) {
            throw new ObjectException("url must not be blank.", Logger.TAG.WARN);
        }
        this.url = url;
    }

    /** Read-only: guild name (resolved). */
    public String getGuildName() {
        return guildName;
    }

    /** Internal: set guild name (resolved). */
    void setGuildName(String guildName) {
        ensureMutable();
        this.guildName = guildName;
    }

    /** Read-only: channel name (resolved). */
    public String getChannelName() {
        return channelName;
    }

    /** Internal: set channel name (resolved). */
    void setChannelName(String channelName) {
        ensureMutable();
        this.channelName = channelName;
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
            throw new ObjectException("InviteObject is sealed and cannot be modified.", Logger.TAG.OBJECT_REJECT);
        }
    }
}
