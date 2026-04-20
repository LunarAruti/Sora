package ucadmin.discord.library;

import java.util.List;

import ucadmin.exceptions.ObjectException;
import ucadmin.util.Logger;

/**
 * Forum object.
 */
public class ForumObject {
    /**
     * Sort order for forum posts.
     */
    public enum SortOrder {
        LATEST_ACTIVITY,
        CREATION_DATE,
        UNKNOWN
    }

    /**
     * Layout style for forums.
     */
    public enum ForumLayout {
        NOT_SET,
        LIST_VIEW,
        GALLERY_VIEW,
        UNKNOWN
    }

    private boolean sealed;
    private long forumId;
    private long guildId;
    private long parentId;
    private String name;
    private String topic;
    private boolean nsfw;
    private int position;
    private int flags;
    private List<String> availableTags;
    private String defaultReactionEmojiName;
    private int defaultThreadSlowmode;
    private int defaultAutoArchiveDuration;
    private SortOrder defaultSortOrder;
    private ForumLayout defaultForumLayout;
    private List<Long> permissionOverwrites;

    /**
     * Empty constructor for deserialization or manual field assignment via setters.
     */
    public ForumObject() {}

    /**
     * Creates a forum object with the core fields you can set at creation time.
     */
    public ForumObject(long guildId, long parentId, String name) {
        setGuildId(guildId);
        setParentId(parentId);
        setName(name);
    }

    /** Read-only: forum id (resolved). */
    public long getForumId() {
        return forumId;
    }

    /** Internal: set forum id (resolved). */
    void setForumId(long forumId) {
        ensureMutable();
        if (forumId < 0L) {
            throw new ObjectException("forumId must be >= 0.", Logger.TAG.WARN);
        }
        this.forumId = forumId;
    }

    /** Guild id for forum creation or resolved forum. */
    public long getGuildId() {
        return guildId;
    }

    /** Set guild id for forum creation. */
    public void setGuildId(long guildId) {
        ensureMutable();
        if (guildId < 0L) {
            throw new ObjectException("guildId must be >= 0.", Logger.TAG.WARN);
        }
        this.guildId = guildId;
    }

    /** Parent id (category). */
    public long getParentId() {
        return parentId;
    }

    /** Set parent id (category). */
    public void setParentId(long parentId) {
        ensureMutable();
        if (parentId < 0L) {
            throw new ObjectException("parentId must be >= 0.", Logger.TAG.WARN);
        }
        this.parentId = parentId;
    }

    /** Forum name. */
    public String getName() {
        return name;
    }

    /** Set forum name. */
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

    /** Forum topic. */
    public String getTopic() {
        return topic;
    }

    /** Set forum topic. */
    public void setTopic(String topic) {
        ensureMutable();
        if (topic != null && topic.length() > 4096) {
            throw new ObjectException("topic must be <= 4096 characters.", Logger.TAG.WARN);
        }
        this.topic = topic;
    }

    /** Whether forum is marked NSFW. */
    public boolean isNsfw() {
        return nsfw;
    }

    /** Set NSFW flag. */
    public void setNsfw(boolean nsfw) {
        ensureMutable();
        this.nsfw = nsfw;
    }

    /** Forum position within the guild/category. */
    public int getPosition() {
        return position;
    }

    /** Set forum position. */
    public void setPosition(int position) {
        ensureMutable();
        if (position < 0) {
            throw new ObjectException("position must be >= 0.", Logger.TAG.WARN);
        }
        this.position = position;
    }

    /** Read-only: forum flags (resolved). */
    public int getFlags() {
        return flags;
    }

    /** Internal: set forum flags (resolved). */
    void setFlags(int flags) {
        ensureMutable();
        if (flags < 0) {
            throw new ObjectException("flags must be >= 0.", Logger.TAG.WARN);
        }
        this.flags = flags;
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

    /** Default reaction emoji name for the forum (custom or unicode). */
    public String getDefaultReactionEmojiName() {
        return defaultReactionEmojiName;
    }

    /** Set default reaction emoji name for the forum (custom or unicode). */
    public void setDefaultReactionEmojiName(String defaultReactionEmojiName) {
        ensureMutable();
        if (defaultReactionEmojiName != null && defaultReactionEmojiName.isBlank()) {
            throw new ObjectException("defaultReactionEmojiName must be null or non-blank.", Logger.TAG.WARN);
        }
        this.defaultReactionEmojiName = defaultReactionEmojiName;
    }

    /** Default slowmode for new posts (seconds). */
    public int getDefaultThreadSlowmode() {
        return defaultThreadSlowmode;
    }

    /** Set default slowmode for new posts (seconds). */
    public void setDefaultThreadSlowmode(int defaultThreadSlowmode) {
        ensureMutable();
        if (defaultThreadSlowmode < 0 || defaultThreadSlowmode > 21600) {
            throw new ObjectException("defaultThreadSlowmode must be between 0 and 21600 seconds.", Logger.TAG.WARN);
        }
        this.defaultThreadSlowmode = defaultThreadSlowmode;
    }

    /** Default auto-archive duration for new posts (minutes). */
    public int getDefaultAutoArchiveDuration() {
        return defaultAutoArchiveDuration;
    }

    /** Set default auto-archive duration for new posts (minutes). */
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

    /** Default forum sort order (null = unset). */
    public SortOrder getDefaultSortOrder() {
        return defaultSortOrder;
    }

    /** Set default forum sort order (null = unset). */
    public void setDefaultSortOrder(SortOrder defaultSortOrder) {
        ensureMutable();
        if (defaultSortOrder == SortOrder.UNKNOWN) {
            throw new ObjectException("defaultSortOrder must be a valid sort order.", Logger.TAG.WARN);
        }
        this.defaultSortOrder = defaultSortOrder;
    }

    /** Default forum layout (null = unset). */
    public ForumLayout getDefaultForumLayout() {
        return defaultForumLayout;
    }

    /** Set default forum layout (null = unset). */
    public void setDefaultForumLayout(ForumLayout defaultForumLayout) {
        ensureMutable();
        if (defaultForumLayout == ForumLayout.UNKNOWN) {
            throw new ObjectException("defaultForumLayout must be a valid layout.", Logger.TAG.WARN);
        }
        this.defaultForumLayout = defaultForumLayout;
    }

    /** Permission overwrites (role/user ids). */
    public List<Long> getPermissionOverwrites() {
        return permissionOverwrites;
    }

    /** Set permission overwrites (role/user ids). */
    public void setPermissionOverwrites(List<Long> permissionOverwrites) {
        ensureMutable();
        this.permissionOverwrites = permissionOverwrites;
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
            throw new ObjectException("ForumObject is sealed and cannot be modified.", Logger.TAG.OBJECT_REJECT);
        }
    }
}
