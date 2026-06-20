package sora.discord.library;

import java.util.List;

import sora.exceptions.ObjectException;
import sora.util.Logger;

/**
 * Message object used by actions for send/resolve workflows.
 */
public class MessageObject {
    private boolean sealed;
    private long messageId;
    private long channelId;
    private long guildId;
    private UserObject author;
    private String content;
    private long timestamp;
    private long editedTimestamp;
    private boolean mentionEveryone;
    private List<Long> mentionedUserIds;
    private List<Long> mentionedRoleIds;
    private List<Long> mentionedChannelIds;
    private int attachmentCount;
    private List<EmbedObject> embeds;
    private List<ReactionObject> reactions;
    private boolean pinned;
    private int flags;
    private String nonce;
    private long webhookId;
    private String messageReference;
    private long referencedMessageId;
    private long threadId;
    private String url;

    /**
     * Empty constructor for deserialization or manual field assignment via setters.
     */
    public MessageObject() {}

    /**
     * Creates a message object with the core fields you can set at creation time.
     */
    public MessageObject(long channelId, String content) {
        setChannelId(channelId);
        setContent(content);
    }

    /** Read-only: message id (resolved). */
    public long getMessageId() {
        return messageId;
    }

    /** Internal: set message id (resolved). */
    void setMessageId(long messageId) {
        ensureMutable();
        if (messageId < 0L) {
            throw new ObjectException("messageId must be >= 0.", Logger.TAG.WARN);
        }
        this.messageId = messageId;
    }

    /** Channel id for sending or resolved message. */
    public long getChannelId() {
        return channelId;
    }

    /** Set channel id for sending. */
    public void setChannelId(long channelId) {
        ensureMutable();
        if (channelId < 0L) {
            throw new ObjectException("channelId must be >= 0.", Logger.TAG.WARN);
        }
        this.channelId = channelId;
    }

    /** Guild id for sending or resolved message. */
    public long getGuildId() {
        return guildId;
    }

    /** Set guild id for sending. */
    public void setGuildId(long guildId) {
        ensureMutable();
        if (guildId < 0L) {
            throw new ObjectException("guildId must be >= 0.", Logger.TAG.WARN);
        }
        this.guildId = guildId;
    }

    /** Read-only: author user (resolved). */
    public UserObject getAuthor() {
        return author;
    }

    /** Internal: set author user (resolved). */
    void setAuthor(UserObject author) {
        ensureMutable();
        this.author = author;
    }

    /** Message content. */
    public String getContent() {
        return content;
    }

    /** Set message content. */
    public void setContent(String content) {
        ensureMutable();
        if (content != null && content.length() > 2000) {
            throw new ObjectException("content must be <= 2000 characters.", Logger.TAG.WARN);
        }
        this.content = content;
    }

    /** Read-only: message creation timestamp (resolved, ms). */
    public long getTimestamp() {
        return timestamp;
    }

    /** Internal: set message creation timestamp (resolved, ms). */
    void setTimestamp(long timestamp) {
        ensureMutable();
        if (timestamp < 0L) {
            throw new ObjectException("timestamp must be >= 0.", Logger.TAG.WARN);
        }
        this.timestamp = timestamp;
    }

    /** Read-only: message edit timestamp (resolved, ms). */
    public long getEditedTimestamp() {
        return editedTimestamp;
    }

    /** Internal: set message edit timestamp (resolved, ms). */
    void setEditedTimestamp(long editedTimestamp) {
        ensureMutable();
        if (editedTimestamp < 0L) {
            throw new ObjectException("editedTimestamp must be >= 0.", Logger.TAG.WARN);
        }
        this.editedTimestamp = editedTimestamp;
    }

    /** Read-only: whether message mentions everyone (resolved). */
    public boolean isMentionEveryone() {
        return mentionEveryone;
    }

    /** Internal: set mention everyone flag (resolved). */
    void setMentionEveryone(boolean mentionEveryone) {
        ensureMutable();
        this.mentionEveryone = mentionEveryone;
    }

    /** Read-only: mentioned user ids (resolved). */
    public List<Long> getMentionedUserIds() {
        return mentionedUserIds;
    }

    /** Internal: set mentioned user ids (resolved). */
    void setMentionedUserIds(List<Long> mentionedUserIds) {
        ensureMutable();
        this.mentionedUserIds = mentionedUserIds;
    }

    /** Read-only: mentioned role ids (resolved). */
    public List<Long> getMentionedRoleIds() {
        return mentionedRoleIds;
    }

    /** Internal: set mentioned role ids (resolved). */
    void setMentionedRoleIds(List<Long> mentionedRoleIds) {
        ensureMutable();
        this.mentionedRoleIds = mentionedRoleIds;
    }

    /** Read-only: mentioned channel ids (resolved). */
    public List<Long> getMentionedChannelIds() {
        return mentionedChannelIds;
    }

    /** Internal: set mentioned channel ids (resolved). */
    void setMentionedChannelIds(List<Long> mentionedChannelIds) {
        ensureMutable();
        this.mentionedChannelIds = mentionedChannelIds;
    }

    /** Read-only: attachment count (resolved). */
    public int getAttachmentCount() {
        return attachmentCount;
    }

    /** Internal: set attachment count (resolved). */
    void setAttachmentCount(int attachmentCount) {
        ensureMutable();
        if (attachmentCount < 0) {
            throw new ObjectException("attachmentCount must be >= 0.", Logger.TAG.WARN);
        }
        this.attachmentCount = attachmentCount;
    }

    /** Embeds for sending. */
    public List<EmbedObject> getEmbeds() {
        return embeds;
    }

    /** Set embeds for sending (max 10). */
    public void setEmbeds(List<EmbedObject> embeds) {
        ensureMutable();
        if (embeds != null && embeds.size() > 10) {
            throw new ObjectException("embeds must be <= 10 items.", Logger.TAG.WARN);
        }
        this.embeds = embeds;
    }

    /** Read-only: reactions (resolved). */
    public List<ReactionObject> getReactions() {
        return reactions;
    }

    /** Internal: set reactions (resolved). */
    void setReactions(List<ReactionObject> reactions) {
        ensureMutable();
        this.reactions = reactions;
    }

    /** Read-only: whether message is pinned (resolved). */
    public boolean isPinned() {
        return pinned;
    }

    /** Internal: set pinned flag (resolved). */
    void setPinned(boolean pinned) {
        ensureMutable();
        this.pinned = pinned;
    }

    /** Message flags (settable by sender where allowed). */
    public int getFlags() {
        return flags;
    }

    /** Set message flags. */
    public void setFlags(int flags) {
        ensureMutable();
        if (flags < 0) {
            throw new ObjectException("flags must be >= 0.", Logger.TAG.WARN);
        }
        this.flags = flags;
    }

    /** Message nonce (client provided for de-duplication/correlation). */
    public String getNonce() {
        return nonce;
    }

    /** Set message nonce (client provided for de-duplication/correlation). */
    public void setNonce(String nonce) {
        ensureMutable();
        this.nonce = nonce;
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

    /** Read-only: message reference (resolved). */
    public String getMessageReference() {
        return messageReference;
    }

    /** Internal: set message reference (resolved). */
    void setMessageReference(String messageReference) {
        ensureMutable();
        this.messageReference = messageReference;
    }

    /** Read-only: referenced message id (resolved). */
    public long getReferencedMessageId() {
        return referencedMessageId;
    }

    /** Internal: set referenced message id (resolved). */
    void setReferencedMessageId(long referencedMessageId) {
        ensureMutable();
        if (referencedMessageId < 0L) {
            throw new ObjectException("referencedMessageId must be >= 0.", Logger.TAG.WARN);
        }
        this.referencedMessageId = referencedMessageId;
    }

    /** Thread id to target (if sending to a thread). */
    public long getThreadId() {
        return threadId;
    }

    /** Set thread id to target (if sending to a thread). */
    public void setThreadId(long threadId) {
        ensureMutable();
        if (threadId < 0L) {
            throw new ObjectException("threadId must be >= 0.", Logger.TAG.WARN);
        }
        this.threadId = threadId;
    }

    /** Read-only: message URL (resolved). */
    public String getUrl() {
        return url;
    }

    /** Internal: set message URL (resolved). */
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
            throw new ObjectException("MessageObject is sealed and cannot be modified.", Logger.TAG.OBJECT_REJECT);
        }
    }
}
