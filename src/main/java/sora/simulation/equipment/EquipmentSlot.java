package sora.simulation.equipment;

public enum EquipmentSlot {
    PRIMARY,
    SECONDARY_1,
    SECONDARY_2,
    SECONDARY_3,
    SECONDARY_4,
    SECONDARY_5,
    SECONDARY_6,
    BACK,
    WAIST,
    LEG,
    CHEST,
    FACE,
    FEET,
    HANDS,
    ACCESSORY;

    public boolean isPrimary() {
        return this == PRIMARY;
    }

    public boolean isSecondary() {
        return switch (this) {
            case SECONDARY_1, SECONDARY_2, SECONDARY_3, SECONDARY_4, SECONDARY_5, SECONDARY_6 -> true;
            default -> false;
        };
    }

    public boolean isBodySlot() {
        return !isPrimary() && !isSecondary();
    }

    public int secondaryIndex() {
        return switch (this) {
            case SECONDARY_1 -> 0;
            case SECONDARY_2 -> 1;
            case SECONDARY_3 -> 2;
            case SECONDARY_4 -> 3;
            case SECONDARY_5 -> 4;
            case SECONDARY_6 -> 5;
            default -> -1;
        };
    }

    public static EquipmentSlot secondarySlot(int index) {
        return switch (index) {
            case 0 -> SECONDARY_1;
            case 1 -> SECONDARY_2;
            case 2 -> SECONDARY_3;
            case 3 -> SECONDARY_4;
            case 4 -> SECONDARY_5;
            case 5 -> SECONDARY_6;
            default -> throw new IllegalArgumentException("Secondary slot index must be 0 through 5.");
        };
    }
}
