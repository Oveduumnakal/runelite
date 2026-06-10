package net.runelite.client.plugins.itemtracker;

public enum GlowSpeed
{
    SLOW("Slow"),
    MEDIUM("Medium"),
    FAST("Fast"),
    OFF("Off");

    private final String displayName;

    GlowSpeed(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
