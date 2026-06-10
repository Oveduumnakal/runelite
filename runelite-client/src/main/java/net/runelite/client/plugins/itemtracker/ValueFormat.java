package net.runelite.client.plugins.itemtracker;

public enum ValueFormat
{
    ABBREVIATED("Short (K,M,B)"),
    FULL("Full (x,xxx)");

    private final String displayName;

    ValueFormat(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
