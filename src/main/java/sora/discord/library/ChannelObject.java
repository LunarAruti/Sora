package sora.discord.library;

import java.util.List;

import sora.exceptions.ObjectException;
import sora.util.Logger;

/**
 * Channel object.
 */
public class ChannelObject {
    /**
     * Channel types that are not threads or forums.
     */
    public enum ChannelType {
        TEXT,
        VOICE,
        CATEGORY,
        ANNOUNCEMENT,
        STAGE,
        DIRECTORY,
        MEDIA,
        UNKNOWN
    }

    /**
     * Video quality mode for voice channels.
     */
    public enum VideoQualityMode {
        AUTO,
        FULL
    }

    private boolean sealed;
    private long channelId;
    private long guildId;
    private long parentId;
    private String name;
    private ChannelType type;
    private int position;
    private String topic;
    private boolean nsfw;
    private int flags;
    private List<Long> permissionOverwritesUsers;
    private List<Long> permissionOverwritesRoles;
    private String url;
    private int slowmode;
    private int defaultAutoArchiveDuration;
    private int defaultThreadSlowmode;
    private List<String> availableTags;
    private String defaultReactionEmojiName;
    private int bitrate;
    private int userLimit;
    private String rtcRegion;
    private VideoQualityMode videoQualityMode;
    private String status;
    private List<Long> childrenIds;

    /**
     * Empty constructor for deserialization or manual field assignment via setters.
     */
    public ChannelObject() {}

    /**
     * Creates a channel object with the core fields you can set at creation time.
     */
    public ChannelObject(long guildId, String name, ChannelType type) {
        setGuildId(guildId);
        setName(name);
        setType(type);
    }

    /** Read-only: channel id (set when resolved). */
    public long getChannelId() {
        return channelId;
    }

    /** Internal: set channel id (resolved). */
    void setChannelId(long channelId) {
        ensureMutable();
        if (channelId < 0L) {
            throw new ObjectException("channelId must be >= 0.", Logger.TAG.WARN);
        }
        this.channelId = channelId;
    }

    /** Guild id for channel creation or resolved channel. */
    public long getGuildId() {
        return guildId;
    }

    /** Set the guild id for channel creation. */
    public void setGuildId(long guildId) {
        ensureMutable();
        if (guildId < 0L) {
            throw new ObjectException("guildId must be >= 0.", Logger.TAG.WARN);
        }
        this.guildId = guildId;
    }

    /** Parent id (category or parent channel). */
    public long getParentId() {
        return parentId;
    }

    /** Set the parent id (category or parent channel). */
    public void setParentId(long parentId) {
        ensureMutable();
        if (parentId < 0L) {
            throw new ObjectException("parentId must be >= 0.", Logger.TAG.WARN);
        }
        this.parentId = parentId;
    }

    /** Channel name. */
    public String getName() {
        return name;
    }

    /** Set the channel name. */
    public void setName(String name) {
        ensureMutable();
        if (name == null || name.isBlank()) {
            throw new ObjectException("name must not be blank.", Logger.TAG.WARN);
        }
        if (name.length() > 100) {
            throw new ObjectException("name must be <= 100 characters.", Logger.TAG.WARN);
        }
        this.name = name;
    }

    /** Channel type (text, voice, category, announcement, stage, directory, media). */
    public ChannelType getType() {
        return type;
    }

    /** Set the channel type (only valid at creation time). */
    public void setType(ChannelType type) {
        ensureMutable();
        if (type == null || type == ChannelType.UNKNOWN) {
            throw new ObjectException("type must be a valid channel type.", Logger.TAG.WARN);
        }
        this.type = type;
    }

    /** Channel position within the guild/category. */
    public int getPosition() {
        return position;
    }

    /** Set channel position. */
    public void setPosition(int position) {
        ensureMutable();
        if (position < 0) {
            throw new ObjectException("position must be >= 0.", Logger.TAG.WARN);
        }
        this.position = position;
    }

    /** Channel topic. */
    public String getTopic() {
        return topic;
    }

    /** Set channel topic. */
    public void setTopic(String topic) {
        ensureMutable();
        if (topic != null && topic.length() > 1024) {
            throw new ObjectException("topic must be <= 1024 characters.", Logger.TAG.WARN);
        }
        this.topic = topic;
    }

    /** Whether channel is marked NSFW. */
    public boolean isNsfw() {
        return nsfw;
    }

    /** Set NSFW flag. */
    public void setNsfw(boolean nsfw) {
        ensureMutable();
        this.nsfw = nsfw;
    }

    /** Read-only: channel flags (resolved from Discord). */
    public int getFlags() {
        return flags;
    }

    /** Internal: set channel flags (resolved). */
    void setFlags(int flags) {
        ensureMutable();
        if (flags < 0) {
            throw new ObjectException("flags must be >= 0.", Logger.TAG.WARN);
        }
        this.flags = flags;
    }

    /** Permission overwrites for users (ids). */
    public List<Long> getPermissionOverwritesUsers() {
        return permissionOverwritesUsers;
    }

    /** Set permission overwrites for users (ids). */
    public void setPermissionOverwritesUsers(List<Long> permissionOverwritesUsers) {
        ensureMutable();
        this.permissionOverwritesUsers = permissionOverwritesUsers;
    }

    /** Permission overwrites for roles (ids). */
    public List<Long> getPermissionOverwritesRoles() {
        return permissionOverwritesRoles;
    }

    /** Set permission overwrites for roles (ids). */
    public void setPermissionOverwritesRoles(List<Long> permissionOverwritesRoles) {
        ensureMutable();
        this.permissionOverwritesRoles = permissionOverwritesRoles;
    }

    /** Read-only: channel URL (resolved). */
    public String getUrl() {
        return url;
    }

    /** Internal: set channel URL (resolved). */
    void setUrl(String url) {
        ensureMutable();
        if (url == null || url.isBlank()) {
            throw new ObjectException("url must not be blank.", Logger.TAG.WARN);
        }
        this.url = url;
    }

    /** Slowmode in seconds. */
    public int getSlowmode() {
        return slowmode;
    }

    /** Set slowmode in seconds. */
    public void setSlowmode(int slowmode) {
        ensureMutable();
        if (slowmode < 0 || slowmode > 21600) {
            throw new ObjectException("slowmode must be between 0 and 21600 seconds.", Logger.TAG.WARN);
        }
        this.slowmode = slowmode;
    }

    /** Default auto-archive duration for threads (minutes). */
    public int getDefaultAutoArchiveDuration() {
        return defaultAutoArchiveDuration;
    }

    /** Set default auto-archive duration for threads (minutes). */
    public void setDefaultAutoArchiveDuration(int defaultAutoArchiveDuration) {
        ensureMutable();
        if (defaultAutoArchiveDuration != 0
                && defaultAutoArchiveDuration != 60
                && defaultAutoArchiveDuration != 1440
                && defaultAutoArchiveDuration != 4320
                && defaultAutoArchiveDuration != 10080) {
            throw new ObjectException(
                    "defaultAutoArchiveDuration must be 60, 1440, 4320, or 10080 minutes (or 0 for unset).",
                    Logger.TAG.WARN);
        }
        this.defaultAutoArchiveDuration = defaultAutoArchiveDuration;
    }

    /** Default slowmode for threads (seconds). */
    public int getDefaultThreadSlowmode() {
        return defaultThreadSlowmode;
    }

    /** Set default slowmode for threads (seconds). */
    public void setDefaultThreadSlowmode(int defaultThreadSlowmode) {
        ensureMutable();
        if (defaultThreadSlowmode < 0 || defaultThreadSlowmode > 21600) {
            throw new ObjectException("defaultThreadSlowmode must be between 0 and 21600 seconds.", Logger.TAG.WARN);
        }
        this.defaultThreadSlowmode = defaultThreadSlowmode;
    }

    /** Available tags for forum channels. */
    public List<String> getAvailableTags() {
        return availableTags;
    }

    /** Set available tags for forum channels. */
    public void setAvailableTags(List<String> availableTags) {
        ensureMutable();
        this.availableTags = availableTags;
    }

    /** Default reaction emoji name for forum channels (custom or unicode). */
    public String getDefaultReactionEmojiName() {
        return defaultReactionEmojiName;
    }

    /** Set default reaction emoji name for forum channels (custom or unicode). */
    public void setDefaultReactionEmojiName(String defaultReactionEmojiName) {
        ensureMutable();
        if (defaultReactionEmojiName != null && defaultReactionEmojiName.isBlank()) {
            throw new ObjectException("defaultReactionEmojiName must be null or non-blank.", Logger.TAG.WARN);
        }
        this.defaultReactionEmojiName = defaultReactionEmojiName;
    }

    /** Voice channel bitrate. */
    public int getBitrate() {
        return bitrate;
    }

    /** Set voice channel bitrate. */
    public void setBitrate(int bitrate) {
        ensureMutable();
        if (bitrate != 0 && (bitrate < 8000 || bitrate > 384000)) {
            throw new ObjectException("bitrate must be between 8000 and 384000 (or 0 for unset).", Logger.TAG.WARN);
        }
        this.bitrate = bitrate;
    }

    /** Voice channel user limit. */
    public int getUserLimit() {
        return userLimit;
    }

    /** Set voice channel user limit. */
    public void setUserLimit(int userLimit) {
        ensureMutable();
        if (userLimit < 0 || userLimit > 99) {
            throw new ObjectException("userLimit must be between 0 and 99.", Logger.TAG.WARN);
        }
        this.userLimit = userLimit;
    }

    /** RTC region override for voice channels. */
    public String getRtcRegion() {
        return rtcRegion;
    }

    /** Set RTC region override for voice channels. */
    public void setRtcRegion(String rtcRegion) {
        ensureMutable();
        this.rtcRegion = rtcRegion;
    }

    /** Video quality mode for voice channels. */
    public VideoQualityMode getVideoQualityMode() {
        return videoQualityMode;
    }

    /** Set video quality mode for voice channels. */
    public void setVideoQualityMode(VideoQualityMode videoQualityMode) {
        ensureMutable();
        this.videoQualityMode = videoQualityMode;
    }

    /** Read-only: channel status (resolved). */
    public String getStatus() {
        return status;
    }

    /** Internal: set channel status (resolved). */
    void setStatus(String status) {
        ensureMutable();
        this.status = status;
    }

    /** Read-only: child channel ids (resolved). */
    public List<Long> getChildrenIds() {
        return childrenIds;
    }

    /** Internal: set child channel ids (resolved). */
    void setChildrenIds(List<Long> childrenIds) {
        ensureMutable();
        this.childrenIds = childrenIds;
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
            throw new ObjectException("ChannelObject is sealed and cannot be modified.", Logger.TAG.OBJECT_REJECT);
        }
    }
}
