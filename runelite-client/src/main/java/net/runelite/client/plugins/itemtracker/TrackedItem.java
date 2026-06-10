package net.runelite.client.plugins.itemtracker;

import lombok.Data;

@Data
public class TrackedItem
{
    private final int itemId;
    private final String name;
    private int quantity;

    private boolean tradeable = true;
    private boolean priceLoadFailed;

    private long highPrice;
    private long lowPrice;
    private long avgPrice;

    private int highDelta;
    private int lowDelta;
    private int avgDelta;
    private long prevHighPrice;
    private long prevLowPrice;
    private long prevAvgPrice;
    private boolean hasDeltas;

    public long getHighValue()
    {
        return (long) quantity * highPrice;
    }

    public long getLowValue()
    {
        return (long) quantity * lowPrice;
    }

    public long getAvgValue()
    {
        return (long) quantity * avgPrice;
    }

    public boolean hasPrices()
    {
        return highPrice > 0 || lowPrice > 0;
    }
}
