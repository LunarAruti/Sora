package ucadmin.discord.library;

import java.util.List;

import ucadmin.exceptions.ObjectException;
import ucadmin.tools.Colors;
import ucadmin.util.Logger;

/**
 * User object.
 */
public class UserObject {
    private boolean sealed;
    private long userId;
    private long guildId;
    private String username;
    private String displayName;
    private String nickname;
    private String discriminator;
    private boolean bot;
    private boolean system;
    private String avatarUrl;
    private Colors.Color accentColor;
    private int flags;
    private long joinedAt;
    private long createdAt;
    private List<Long> roleList;
    private boolean deaf;
    private boolean mute;
    private long permissions;

    /**
     * Empty constructor for deserialization.
     */
    public UserObject() {}

    /** Read-only: user id (resolved). */
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

    /** Read-only: guild id (resolved, 0 if not in guild context). */
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

    /** Read-only: username (resolved). */
    public String getUsername() {
        return username;
    }

    /** Internal: set username (resolved). */
    void setUsername(String username) {
        ensureMutable();
        if (username == null || username.isBlank()) {
            throw new ObjectException("username must not be blank.", Logger.TAG.WARN);
        }
        this.username = username;
    }

    /** Read-only: display name (resolved). */
    public String getDisplayName() {
        return displayName;
    }

    /** Internal: set display name (resolved). */
    void setDisplayName(String displayName) {
        ensureMutable();
        this.displayName = displayName;
    }

    /** Read-only: nickname (resolved, may be blank). */
    public String getNickname() {
        return nickname;
    }

    /** Internal: set nickname (resolved, may be blank). */
    void setNickname(String nickname) {
        ensureMutable();
        this.nickname = nickname;
    }

    /** Read-only: discriminator (resolved, may be blank). */
    public String getDiscriminator() {
        return discriminator;
    }

    /** Internal: set discriminator (resolved, may be blank). */
    void setDiscriminator(String discriminator) {
        ensureMutable();
        this.discriminator = discriminator;
    }

    /** Read-only: whether this user is a bot (resolved). */
    public boolean isBot() {
        return bot;
    }

    /** Internal: set bot flag (resolved). */
    void setBot(boolean bot) {
        ensureMutable();
        this.bot = bot;
    }

    /** Read-only: whether this user is a system user (resolved). */
    public boolean isSystem() {
        return system;
    }

    /** Internal: set system flag (resolved). */
    void setSystem(boolean system) {
        ensureMutable();
        this.system = system;
    }

    /** Read-only: avatar URL (resolved, may be blank). */
    public String getAvatarUrl() {
        return avatarUrl;
    }

    /** Internal: set avatar URL (resolved, may be blank). */
    void setAvatarUrl(String avatarUrl) {
        ensureMutable();
        this.avatarUrl = avatarUrl;
    }

    /** Read-only: accent color (resolved, may be null). */
    public Colors.Color getAccentColor() {
        return accentColor;
    }

    /** Internal: set accent color (resolved). */
    void setAccentColor(Colors.Color accentColor) {
        ensureMutable();
        this.accentColor = accentColor;
    }

    /** Read-only: user flags (resolved). */
    public int getFlags() {
        return flags;
    }

    /** Internal: set user flags (resolved). */
    void setFlags(int flags) {
        ensureMutable();
        if (flags < 0) {
            throw new ObjectException("flags must be >= 0.", Logger.TAG.WARN);
        }
        this.flags = flags;
    }

    /** Read-only: joined timestamp (resolved, 0 if not in guild). */
    public long getJoinedAt() {
        return joinedAt;
    }

    /** Internal: set joined timestamp (resolved). */
    void setJoinedAt(long joinedAt) {
        ensureMutable();
        if (joinedAt < 0L) {
            throw new ObjectException("joinedAt must be >= 0.", Logger.TAG.WARN);
        }
        this.joinedAt = joinedAt;
    }

    /** Read-only: created timestamp (resolved). */
    public long getCreatedAt() {
        return createdAt;
    }

    /** Internal: set created timestamp (resolved). */
    void setCreatedAt(long createdAt) {
        ensureMutable();
        if (createdAt < 0L) {
            throw new ObjectException("createdAt must be >= 0.", Logger.TAG.WARN);
        }
        this.createdAt = createdAt;
    }

    /** Read-only: role list (resolved, empty or null if not in guild). */
    public List<Long> getRoleList() {
        return roleList;
    }

    /** Internal: set role list (resolved). */
    void setRoleList(List<Long> roleList) {
        ensureMutable();
        this.roleList = roleList;
    }

    /** Read-only: whether the user is deafened (resolved, false if not in guild). */
    public boolean isDeaf() {
        return deaf;
    }

    /** Internal: set deafened flag (resolved). */
    void setDeaf(boolean deaf) {
        ensureMutable();
        this.deaf = deaf;
    }

    /** Read-only: whether the user is muted (resolved, false if not in guild). */
    public boolean isMute() {
        return mute;
    }

    /** Internal: set muted flag (resolved). */
    void setMute(boolean mute) {
        ensureMutable();
        this.mute = mute;
    }

    /** Read-only: permissions (resolved, 0 if not in guild). */
    public long getPermissions() {
        return permissions;
    }

    /** Internal: set permissions (resolved). */
    void setPermissions(long permissions) {
        ensureMutable();
        if (permissions < 0L) {
            throw new ObjectException("permissions must be >= 0.", Logger.TAG.WARN);
        }
        this.permissions = permissions;
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
            throw new ObjectException("UserObject is sealed and cannot be modified.", Logger.TAG.OBJECT_REJECT);
        }
    }
}
