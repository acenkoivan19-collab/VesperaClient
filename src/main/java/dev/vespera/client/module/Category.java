package dev.vespera.client.module;

public enum Category {
    ALL("All modules"),
    COMBAT("Combat"),
    VISUALS("Visuals"),
    WORLD("World"),
    INTERFACE("Interface"),
    UTILITY("Utility"),
    COSMETICS("Cosmetics"),
    APPEARANCE("Appearance");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
