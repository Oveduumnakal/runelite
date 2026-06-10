package net.runelite.client.plugins.itemtracker;

public enum PriceIndicatorMode
{
    ALL("All"),
    CHANGE("Change"),
    OFF("Off");

    private final String displayName;

    PriceIndicatorMode(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
