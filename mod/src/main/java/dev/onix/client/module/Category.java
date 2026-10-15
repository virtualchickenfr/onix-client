package dev.onix.client.module;

public enum Category {
    HUD("HUD", "hud", "On-screen information"),
    COMBAT("Combat", "combat", "PvP helpers and feedback"),
    VISUAL("Visual", "visual", "How the world looks"),
    CAMERA("Camera", "camera", "Zoom, freelook and perspective"),
    CHAT("Chat", "chat", "Chat tools and filters"),
    UTILITY("Utility", "utility", "Quality of life"),
    PERFORMANCE("Performance", "performance", "Frame rate and resources");

    private final String displayName;
    private final String icon;
    private final String description;

    Category(String displayName, String icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getIcon() { return icon; }
    public String getDescription() { return description; }
}
