package net.runelite.client.plugins.itemtracker;

public enum PriceDisplay
{
    AVERAGE("Average only"),
    HIGH_LOW("High / Low only"),
    BOTH("High, Low & Average");

    private final String label;

    PriceDisplay(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
