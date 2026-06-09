package net.runelite.client.plugins.itemtracker;

public enum ValueFormat
{
    ABBREVIATED("Abbreviated (1.5K, 2.3M)"),
    FULL("Full (1,500, 2,300,000)");

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
