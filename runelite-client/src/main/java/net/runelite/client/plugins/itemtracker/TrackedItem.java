package net.runelite.client.plugins.itemtracker;

import lombok.Data;

@Data
public class TrackedItem
{
    private final int itemId;
    private final String name;
    private int quantity;

    /** Latest insta-buy (high) price per item from the wiki real-time API */
    private long highPrice;
    /** Latest insta-sell (low) price per item from the wiki real-time API */
    private long lowPrice;
    /** Average of high + low per item */
    private long avgPrice;

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

    /** True if any price data has been loaded. */
    public boolean hasPrices()
    {
        return highPrice > 0 || lowPrice > 0;
    }
}
