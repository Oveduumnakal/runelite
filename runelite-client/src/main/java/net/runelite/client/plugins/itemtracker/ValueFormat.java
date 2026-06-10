package net.runelite.client.plugins.itemtracker;

public enum ValueFormat
{
    ABBREVIATED("Short (k,m,b)"),
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
