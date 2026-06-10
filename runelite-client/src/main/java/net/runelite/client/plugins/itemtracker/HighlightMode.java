package net.runelite.client.plugins.itemtracker;

public enum HighlightMode
{
    GROUND("Ground"),
    INV_BANK("Inv/Bank"),
    BOTH("Both"),
    OFF("Off");

    private final String displayName;

    HighlightMode(String displayName)
    {
        this.displayName = displayName;
    }

    /** True if ground items should be highlighted. */
    public boolean ground()
    {
        return this == GROUND || this == BOTH;
    }

    /** True if inventory and bank items should be highlighted. */
    public boolean invBank()
    {
        return this == INV_BANK || this == BOTH;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
