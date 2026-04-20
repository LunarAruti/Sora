package ucadmin.discord.library;

import java.util.List;

import ucadmin.exceptions.ObjectException;
import ucadmin.util.Logger;

/**
 * Thread object.
 */
public class ThreadObject {
    /**
     * Thread type.
     */
    public enum ThreadType {
        PUBLIC,
        PRIVATE,
        ANNOUNCEMENT,
        UNKNOWN
    }

    private boolean sealed;
    private long threadId;
    private long guildId;
    private long parentId;
    private UserObject author;
    private String name;
    private ThreadType type;
    private boolean archived;
    private boolean locked;
    private boolean invitable;
    private int autoArchiveDuration;
    private long archiveTimestamp;
    private List<Long> memberList;
    private long createdAt;
    private long lastMessageId;
    private String url;

    /**
     * Empty constructor for deserialization or manual field assignment via setters.
     */
    public ThreadObject() {}

    /**
     * Creates a thread object with the core fields you can set at creation time.
     */
    public ThreadObject(long guildId, long parentId, String name, ThreadType type) {
        setGuildId(guildId);
        setParentId(parentId);
        setName(name);
        setType(type);
    }

    /** Read-only: thread id (resolved). */
    public long getThreadId() {
        return threadId;
    }

    /** Internal: set thread id (resolved). */
    void setThreadId(long threadId) {
        ensureMutable();
        if (threadId < 0L) {
            throw new ObjectException("threadId must be >= 0.", Logger.TAG.WARN);
        }
        this.threadId = threadId;
    }

    /** Guild id for thread creation or resolved thread. */
    public long getGuildId() {
        return guildId;
    }

    /** Set guild id for thread creation. */
    public void setGuildId(long guildId) {
        ensureMutable();
        if (guildId < 0L) {
            throw new ObjectException("guildId must be >= 0.", Logger.TAG.WARN);
        }
        this.guildId = guildId;
    }

    /** Parent channel id for this thread. */
    public long getParentId() {
        return parentId;
    }

    /** Set parent channel id. */
    public void setParentId(long parentId) {
        ensureMutable();
        if (parentId < 0L) {
            throw new ObjectException("parentId must be >= 0.", Logger.TAG.WARN);
        }
        this.parentId = parentId;
    }

    /** Read-only: thread author (resolved). */
    public UserObject getAuthor() {
        return author;
    }

    /** Internal: set thread author (resolved). */
    void setAuthor(UserObject author) {
        ensureMutable();
        this.author = author;
    }

    /** Thread name. */
    public String getName() {
        return name;
    }

    /** Set thread name. */
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

    /** Thread type (public, private, announcement). */
    public ThreadType getType() {
        return type;
    }

    /** Set thread type (creation only). */
    public void setType(ThreadType type) {
        ensureMutable();
        if (type == null || type == ThreadType.UNKNOWN) {
            throw new ObjectException("type must be a valid thread type.", Logger.TAG.WARN);
        }
        this.type = type;
    }

    /** Read-only: whether the thread is archived (resolved). */
    public boolean isArchived() {
        return archived;
    }

    /** Internal: set archived flag (resolved). */
    void setArchived(boolean archived) {
        ensureMutable();
        this.archived = archived;
    }

    /** Read-only: whether the thread is locked (resolved). */
    public boolean isLocked() {
        return locked;
    }

    /** Internal: set locked flag (resolved). */
    void setLocked(boolean locked) {
        ensureMutable();
        this.locked = locked;
    }

    /** Whether the thread is invitable (private threads). */
    public boolean isInvitable() {
        return invitable;
    }

    /** Set invitable flag (private threads). */
    public void setInvitable(boolean invitable) {
        ensureMutable();
        this.invitable = invitable;
    }

    /** Auto-archive duration in minutes. */
    public int getAutoArchiveDuration() {
        return autoArchiveDuration;
    }

    /** Set auto-archive duration in minutes. */
    public void setAutoArchiveDuration(int autoArchiveDuration) {
        ensureMutable();
        if (autoArchiveDuration != 0
                && autoArchiveDuration != 60
                && autoArchiveDuration != 1440
                && autoArchiveDuration != 4320
                && autoArchiveDuration != 10080) {
            throw new ObjectException(
                    "autoArchiveDuration must be 60, 1440, 4320, or 10080 minutes (or 0 for unset).",
                    Logger.TAG.WARN);
        }
        this.autoArchiveDuration = autoArchiveDuration;
    }

    /** Read-only: archive timestamp (resolved). */
    public long getArchiveTimestamp() {
        return archiveTimestamp;
    }

    /** Internal: set archive timestamp (resolved). */
    void setArchiveTimestamp(long archiveTimestamp) {
        ensureMutable();
        if (archiveTimestamp < 0L) {
            throw new ObjectException("archiveTimestamp must be >= 0.", Logger.TAG.WARN);
        }
        this.archiveTimestamp = archiveTimestamp;
    }

    /** Read-only: thread member list (resolved). */
    public List<Long> getMemberList() {
        return memberList;
    }

    /** Internal: set thread member list (resolved). */
    void setMemberList(List<Long> memberList) {
        ensureMutable();
        this.memberList = memberList;
    }

    /** Read-only: creation timestamp (resolved). */
    public long getCreatedAt() {
        return createdAt;
    }

    /** Internal: set creation timestamp (resolved). */
    void setCreatedAt(long createdAt) {
        ensureMutable();
        if (createdAt < 0L) {
            throw new ObjectException("createdAt must be >= 0.", Logger.TAG.WARN);
        }
        this.createdAt = createdAt;
    }

    /** Read-only: last message id (resolved). */
    public long getLastMessageId() {
        return lastMessageId;
    }

    /** Internal: set last message id (resolved). */
    void setLastMessageId(long lastMessageId) {
        ensureMutable();
        if (lastMessageId < 0L) {
            throw new ObjectException("lastMessageId must be >= 0.", Logger.TAG.WARN);
        }
        this.lastMessageId = lastMessageId;
    }

    /** Read-only: thread URL (resolved). */
    public String getUrl() {
        return url;
    }

    /** Internal: set thread URL (resolved). */
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
            throw new ObjectException("ThreadObject is sealed and cannot be modified.", Logger.TAG.OBJECT_REJECT);
        }
    }
}
