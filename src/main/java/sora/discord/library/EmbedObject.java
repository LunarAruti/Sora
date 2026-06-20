package sora.discord.library;

import java.util.List;

import sora.exceptions.ObjectException;
import sora.tools.Colors;
import sora.util.Logger;

/**
 * Embed object.
 */
public class EmbedObject {
    private boolean sealed;
    private long embedId;
    private long messageId;
    private long channelId;
    private long guildId;
    private String messageUrl;
    private UserObject author;
    private long messageTimestamp;
    private String title;
    private String description;
    private String url;
    private Colors.Color color;
    private String authorText;
    private String authorUrl;
    private String authorPicture;
    private String footer;
    private String thumbnailUrl;
    private String imageUrl;
    private boolean timestampEnabled;
    private int fieldCount;
    private List<FieldObject> fields;

    /**
     * Empty constructor for deserialization or manual field assignment via setters.
     */
    public EmbedObject() {}

    /**
     * Creates an embed object with the core fields you can set at creation time.
     */
    public EmbedObject(String title, String description) {
        setTitle(title);
        setDescription(description);
    }

    /** Read-only: embed id (resolved). */
    public long getEmbedId() {
        return embedId;
    }

    /** Internal: set embed id (resolved). */
    void setEmbedId(long embedId) {
        ensureMutable();
        if (embedId < 0L) {
            throw new ObjectException("embedId must be >= 0.", Logger.TAG.WARN);
        }
        this.embedId = embedId;
    }

    /** Read-only: message id this embed belongs to (resolved). */
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

    /** Read-only: channel id this embed belongs to (resolved). */
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

    /** Read-only: guild id this embed belongs to (resolved). */
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

    /** Read-only: message URL this embed belongs to (resolved). */
    public String getMessageUrl() {
        return messageUrl;
    }

    /** Internal: set message URL (resolved). */
    void setMessageUrl(String messageUrl) {
        ensureMutable();
        if (messageUrl == null || messageUrl.isBlank()) {
            throw new ObjectException("messageUrl must not be blank.", Logger.TAG.WARN);
        }
        this.messageUrl = messageUrl;
    }

    /** Read-only: message timestamp (resolved, ms). */
    public long getMessageTimestamp() {
        return messageTimestamp;
    }

    /** Internal: set message timestamp (resolved, ms). */
    void setMessageTimestamp(long messageTimestamp) {
        ensureMutable();
        if (messageTimestamp < 0L) {
            throw new ObjectException("messageTimestamp must be >= 0.", Logger.TAG.WARN);
        }
        this.messageTimestamp = messageTimestamp;
    }

    /** Read-only: embed author user (resolved). */
    public UserObject getAuthor() {
        return author;
    }

    /** Internal: set embed author user (resolved). */
    void setAuthor(UserObject author) {
        ensureMutable();
        this.author = author;
    }

    /** Embed title. */
    public String getTitle() {
        return title;
    }

    /** Set embed title. */
    public void setTitle(String title) {
        ensureMutable();
        if (title != null && title.length() > 256) {
            throw new ObjectException("title must be <= 256 characters.", Logger.TAG.WARN);
        }
        this.title = title;
    }

    /** Embed description. */
    public String getDescription() {
        return description;
    }

    /** Set embed description. */
    public void setDescription(String description) {
        ensureMutable();
        if (description != null && description.length() > 4096) {
            throw new ObjectException("description must be <= 4096 characters.", Logger.TAG.WARN);
        }
        this.description = description;
    }

    /** Embed URL. */
    public String getUrl() {
        return url;
    }

    /** Set embed URL. */
    public void setUrl(String url) {
        ensureMutable();
        this.url = url;
    }

    /** Embed color. */
    public Colors.Color getColor() {
        return color;
    }

    /** Set embed color. */
    public void setColor(Colors.Color color) {
        ensureMutable();
        this.color = color;
    }

    /** Author text. */
    public String getAuthorText() {
        return authorText;
    }

    /** Set author text. */
    public void setAuthorText(String authorText) {
        ensureMutable();
        if (authorText != null && authorText.length() > 256) {
            throw new ObjectException("authorText must be <= 256 characters.", Logger.TAG.WARN);
        }
        this.authorText = authorText;
    }

    /** Author URL. */
    public String getAuthorUrl() {
        return authorUrl;
    }

    /** Set author URL. */
    public void setAuthorUrl(String authorUrl) {
        ensureMutable();
        this.authorUrl = authorUrl;
    }

    /** Author picture URL. */
    public String getAuthorPicture() {
        return authorPicture;
    }

    /** Set author picture URL. */
    public void setAuthorPicture(String authorPicture) {
        ensureMutable();
        this.authorPicture = authorPicture;
    }

    /** Footer text. */
    public String getFooter() {
        return footer;
    }

    /** Set footer text. */
    public void setFooter(String footer) {
        ensureMutable();
        if (footer != null && footer.length() > 2048) {
            throw new ObjectException("footer must be <= 2048 characters.", Logger.TAG.WARN);
        }
        this.footer = footer;
    }

    /** Thumbnail URL. */
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    /** Set thumbnail URL. */
    public void setThumbnailUrl(String thumbnailUrl) {
        ensureMutable();
        this.thumbnailUrl = thumbnailUrl;
    }

    /** Image URL. */
    public String getImageUrl() {
        return imageUrl;
    }

    /** Set image URL. */
    public void setImageUrl(String imageUrl) {
        ensureMutable();
        this.imageUrl = imageUrl;
    }

    /** Whether to include a timestamp in the embed. */
    public boolean isTimestampEnabled() {
        return timestampEnabled;
    }

    /** Set whether to include a timestamp in the embed. */
    public void setTimestampEnabled(boolean timestampEnabled) {
        ensureMutable();
        this.timestampEnabled = timestampEnabled;
    }

    /** Count of fields in the embed. */
    public int getFieldCount() {
        return fieldCount;
    }

    /** Internal: set field count (resolved or calculated). */
    void setFieldCount(int fieldCount) {
        ensureMutable();
        if (fieldCount < 0) {
            throw new ObjectException("fieldCount must be >= 0.", Logger.TAG.WARN);
        }
        this.fieldCount = fieldCount;
    }

    /** Embed fields. */
    public List<FieldObject> getFields() {
        return fields;
    }

    /** Set embed fields (max 25). */
    public void setFields(List<FieldObject> fields) {
        ensureMutable();
        if (fields != null && fields.size() > 25) {
            throw new ObjectException("fields must be <= 25 items.", Logger.TAG.WARN);
        }
        this.fields = fields;
        setFieldCount(fields == null ? 0 : fields.size());
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
            throw new ObjectException("EmbedObject is sealed and cannot be modified.", Logger.TAG.OBJECT_REJECT);
        }
    }
}
