package sora.discord.library;

import sora.exceptions.ObjectException;
import sora.util.Logger;

/**
 * Guild object.
 */
public class GuildObject {
    private boolean sealed;
    private long guildId;
    private String name;
    private String description;
    private String icon;
    private long ownerId;
    private String vanityUrlCode;
    private String preferredLocale;
    private String verificationLevel;
    private int premiumTier;
    private int premiumSubscriptionCount;
    private long systemChannelId;
    private long rulesChannelId;
    private long publicUpdatesChannelId;
    private long afkChannelId;
    private int afkTimeout;
    private int memberCount;
    private int presenceCount;

    /**
     * Empty constructor for deserialization.
     */
    public GuildObject() {}

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

    /** Read-only: guild name (resolved). */
    public String getName() {
        return name;
    }

    /** Internal: set guild name (resolved). */
    void setName(String name) {
        ensureMutable();
        if (name == null || name.isBlank()) {
            throw new ObjectException("name must not be blank.", Logger.TAG.WARN);
        }
        if (name.length() > 100) {
            throw new ObjectException("name must be <= 100 characters.", Logger.TAG.WARN);
        }
        this.name = name;
    }

    /** Read-only: guild description (resolved). */
    public String getDescription() {
        return description;
    }

    /** Internal: set guild description (resolved). */
    void setDescription(String description) {
        ensureMutable();
        this.description = description;
    }

    /** Read-only: guild icon (resolved). */
    public String getIcon() {
        return icon;
    }

    /** Internal: set guild icon (resolved). */
    void setIcon(String icon) {
        ensureMutable();
        this.icon = icon;
    }

    /** Read-only: guild owner id (resolved). */
    public long getOwnerId() {
        return ownerId;
    }

    /** Internal: set guild owner id (resolved). */
    void setOwnerId(long ownerId) {
        ensureMutable();
        if (ownerId < 0L) {
            throw new ObjectException("ownerId must be >= 0.", Logger.TAG.WARN);
        }
        this.ownerId = ownerId;
    }

    /** Read-only: vanity URL code (resolved). */
    public String getVanityUrlCode() {
        return vanityUrlCode;
    }

    /** Internal: set vanity URL code (resolved). */
    void setVanityUrlCode(String vanityUrlCode) {
        ensureMutable();
        this.vanityUrlCode = vanityUrlCode;
    }

    /** Read-only: preferred locale (resolved). */
    public String getPreferredLocale() {
        return preferredLocale;
    }

    /** Internal: set preferred locale (resolved). */
    void setPreferredLocale(String preferredLocale) {
        ensureMutable();
        this.preferredLocale = preferredLocale;
    }

    /** Read-only: verification level (resolved). */
    public String getVerificationLevel() {
        return verificationLevel;
    }

    /** Internal: set verification level (resolved). */
    void setVerificationLevel(String verificationLevel) {
        ensureMutable();
        this.verificationLevel = verificationLevel;
    }

    /** Read-only: premium tier (resolved). */
    public int getPremiumTier() {
        return premiumTier;
    }

    /** Internal: set premium tier (resolved). */
    void setPremiumTier(int premiumTier) {
        ensureMutable();
        if (premiumTier < 0) {
            throw new ObjectException("premiumTier must be >= 0.", Logger.TAG.WARN);
        }
        this.premiumTier = premiumTier;
    }

    /** Read-only: premium subscription count (resolved). */
    public int getPremiumSubscriptionCount() {
        return premiumSubscriptionCount;
    }

    /** Internal: set premium subscription count (resolved). */
    void setPremiumSubscriptionCount(int premiumSubscriptionCount) {
        ensureMutable();
        if (premiumSubscriptionCount < 0) {
            throw new ObjectException("premiumSubscriptionCount must be >= 0.", Logger.TAG.WARN);
        }
        this.premiumSubscriptionCount = premiumSubscriptionCount;
    }

    /** Read-only: system channel id (resolved). */
    public long getSystemChannelId() {
        return systemChannelId;
    }

    /** Internal: set system channel id (resolved). */
    void setSystemChannelId(long systemChannelId) {
        ensureMutable();
        if (systemChannelId < 0L) {
            throw new ObjectException("systemChannelId must be >= 0.", Logger.TAG.WARN);
        }
        this.systemChannelId = systemChannelId;
    }

    /** Read-only: rules channel id (resolved). */
    public long getRulesChannelId() {
        return rulesChannelId;
    }

    /** Internal: set rules channel id (resolved). */
    void setRulesChannelId(long rulesChannelId) {
        ensureMutable();
        if (rulesChannelId < 0L) {
            throw new ObjectException("rulesChannelId must be >= 0.", Logger.TAG.WARN);
        }
        this.rulesChannelId = rulesChannelId;
    }

    /** Read-only: public updates channel id (resolved). */
    public long getPublicUpdatesChannelId() {
        return publicUpdatesChannelId;
    }

    /** Internal: set public updates channel id (resolved). */
    void setPublicUpdatesChannelId(long publicUpdatesChannelId) {
        ensureMutable();
        if (publicUpdatesChannelId < 0L) {
            throw new ObjectException("publicUpdatesChannelId must be >= 0.", Logger.TAG.WARN);
        }
        this.publicUpdatesChannelId = publicUpdatesChannelId;
    }

    /** Read-only: AFK channel id (resolved). */
    public long getAfkChannelId() {
        return afkChannelId;
    }

    /** Internal: set AFK channel id (resolved). */
    void setAfkChannelId(long afkChannelId) {
        ensureMutable();
        if (afkChannelId < 0L) {
            throw new ObjectException("afkChannelId must be >= 0.", Logger.TAG.WARN);
        }
        this.afkChannelId = afkChannelId;
    }

    /** Read-only: AFK timeout (seconds, resolved). */
    public int getAfkTimeout() {
        return afkTimeout;
    }

    /** Internal: set AFK timeout (resolved). */
    void setAfkTimeout(int afkTimeout) {
        ensureMutable();
        if (afkTimeout < 0) {
            throw new ObjectException("afkTimeout must be >= 0.", Logger.TAG.WARN);
        }
        this.afkTimeout = afkTimeout;
    }

    /** Read-only: member count (resolved). */
    public int getMemberCount() {
        return memberCount;
    }

    /** Internal: set member count (resolved). */
    void setMemberCount(int memberCount) {
        ensureMutable();
        if (memberCount < 0) {
            throw new ObjectException("memberCount must be >= 0.", Logger.TAG.WARN);
        }
        this.memberCount = memberCount;
    }

    /** Read-only: presence count (resolved). */
    public int getPresenceCount() {
        return presenceCount;
    }

    /** Internal: set presence count (resolved). */
    void setPresenceCount(int presenceCount) {
        ensureMutable();
        if (presenceCount < 0) {
            throw new ObjectException("presenceCount must be >= 0.", Logger.TAG.WARN);
        }
        this.presenceCount = presenceCount;
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
            throw new ObjectException("GuildObject is sealed and cannot be modified.", Logger.TAG.OBJECT_REJECT);
        }
    }
}
