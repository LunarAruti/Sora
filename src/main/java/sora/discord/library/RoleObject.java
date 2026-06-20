package sora.discord.library;

import java.util.List;

import sora.exceptions.ObjectException;
import sora.tools.Colors;
import sora.util.Logger;

/**
 * Role object.
 */
public class RoleObject {
    private boolean sealed;
    private long roleId;
    private long guildId;
    private String name;
    private Colors.Color color;
    private boolean hoist;
    private String icon;
    private String emojiName;
    private int position;
    private List<String> permissionsList;
    private boolean managed;
    private boolean mentionable;
    private List<Long> memberList;

    /**
     * Empty constructor for deserialization or manual field assignment via setters.
     */
    public RoleObject() {}

    /**
     * Creates a role object with the core fields you can set at creation time.
     */
    public RoleObject(long guildId, String name) {
        setGuildId(guildId);
        setName(name);
    }

    /** Read-only: role id (resolved). */
    public long getRoleId() {
        return roleId;
    }

    /** Internal: set role id (resolved). */
    void setRoleId(long roleId) {
        ensureMutable();
        if (roleId < 0L) {
            throw new ObjectException("roleId must be >= 0.", Logger.TAG.WARN);
        }
        this.roleId = roleId;
    }

    /** Guild id for role creation or resolved role. */
    public long getGuildId() {
        return guildId;
    }

    /** Set guild id for role creation. */
    public void setGuildId(long guildId) {
        ensureMutable();
        if (guildId < 0L) {
            throw new ObjectException("guildId must be >= 0.", Logger.TAG.WARN);
        }
        this.guildId = guildId;
    }

    /** Role name. */
    public String getName() {
        return name;
    }

    /** Set role name. */
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

    /** Role color. */
    public Colors.Color getColor() {
        return color;
    }

    /** Set role color. */
    public void setColor(Colors.Color color) {
        ensureMutable();
        this.color = color;
    }

    /** Whether the role is hoisted. */
    public boolean isHoist() {
        return hoist;
    }

    /** Set hoist flag. */
    public void setHoist(boolean hoist) {
        ensureMutable();
        this.hoist = hoist;
    }

    /** Role icon. */
    public String getIcon() {
        return icon;
    }

    /** Set role icon. */
    public void setIcon(String icon) {
        ensureMutable();
        this.icon = icon;
    }

    /** Role emoji name (custom or unicode). */
    public String getEmojiName() {
        return emojiName;
    }

    /** Set role emoji name (custom or unicode). */
    public void setEmojiName(String emojiName) {
        ensureMutable();
        this.emojiName = emojiName;
    }

    /** Role position. */
    public int getPosition() {
        return position;
    }

    /** Set role position. */
    public void setPosition(int position) {
        ensureMutable();
        if (position < 0) {
            throw new ObjectException("position must be >= 0.", Logger.TAG.WARN);
        }
        this.position = position;
    }

    /** Role permissions list (names/keys). */
    public List<String> getPermissionsList() {
        return permissionsList;
    }

    /** Set role permissions list (names/keys). */
    public void setPermissionsList(List<String> permissionsList) {
        ensureMutable();
        this.permissionsList = permissionsList;
    }

    /** Read-only: whether the role is managed (resolved). */
    public boolean isManaged() {
        return managed;
    }

    /** Internal: set managed flag (resolved). */
    void setManaged(boolean managed) {
        ensureMutable();
        this.managed = managed;
    }

    /** Whether the role is mentionable. */
    public boolean isMentionable() {
        return mentionable;
    }

    /** Set mentionable flag. */
    public void setMentionable(boolean mentionable) {
        ensureMutable();
        this.mentionable = mentionable;
    }

    /** Read-only: role member list (resolved). */
    public List<Long> getMemberList() {
        return memberList;
    }

    /** Internal: set role member list (resolved). */
    void setMemberList(List<Long> memberList) {
        ensureMutable();
        this.memberList = memberList;
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
            throw new ObjectException("RoleObject is sealed and cannot be modified.", Logger.TAG.OBJECT_REJECT);
        }
    }
}
