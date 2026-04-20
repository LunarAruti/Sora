package ucadmin.discord.library;

import ucadmin.exceptions.ObjectException;
import ucadmin.util.Logger;

/**
 * Embed field object used within embed fields.
 */
public class FieldObject {
    private boolean sealed;
    private String name;
    private String value;
    private boolean inline;

    /**
     * Empty constructor for deserialization or manual field assignment via setters.
     */
    public FieldObject() {}

    /**
     * Creates a field object with a name and value.
     */
    public FieldObject(String name, String value, boolean inline) {
        setName(name);
        setValue(value);
        this.inline = inline;
    }

    /** Field name. */
    public String getName() {
        return name;
    }

    /** Set field name (max 256). */
    public void setName(String name) {
        ensureMutable();
        if (name == null || name.isBlank()) {
            throw new ObjectException("field name must not be blank.", Logger.TAG.WARN);
        }
        if (name.length() > 256) {
            throw new ObjectException("field name must be <= 256 characters.", Logger.TAG.WARN);
        }
        this.name = name;
    }

    /** Field value. */
    public String getValue() {
        return value;
    }

    /** Set field value (max 1024). */
    public void setValue(String value) {
        ensureMutable();
        if (value == null || value.isBlank()) {
            throw new ObjectException("field value must not be blank.", Logger.TAG.WARN);
        }
        if (value.length() > 1024) {
            throw new ObjectException("field value must be <= 1024 characters.", Logger.TAG.WARN);
        }
        this.value = value;
    }

    /** Whether this field is inline. */
    public boolean isInline() {
        return inline;
    }

    /** Set inline flag. */
    public void setInline(boolean inline) {
        ensureMutable();
        this.inline = inline;
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
            throw new ObjectException("FieldObject is sealed and cannot be modified.", Logger.TAG.OBJECT_REJECT);
        }
    }
}
