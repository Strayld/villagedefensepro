package fr.obsidianlabs.plugins.villagedefense.arena;

public enum ArenaAvailability {

    ACTIVE,
    TEST,
    DISABLED;

    public ArenaAvailability next() {
        return switch (this) {
            case ACTIVE -> TEST;
            case TEST -> DISABLED;
            case DISABLED -> ACTIVE;
        };
    }
}
