package net.runelite.client.plugins.itemtracker;

import lombok.Data;

@Data
public class TrackedItem
{
    private final int itemId;
    private final String name;
    private int quantity;

    /** Whether the item can be traded on the GE (set when the item is added). */
    private boolean tradeable = true;

    /** True if the last price fetch completed but no price could be loaded for this item. */
    private boolean priceLoadFailed;

    /** Latest insta-buy (high) price per item from the wiki real-time API */
    private long highPrice;
    /** Latest insta-sell (low) price per item from the wiki real-time API */
    private long lowPrice;
    /** Average of high + low per item */
    private long avgPrice;

    /** Price movement since the previous refresh: -1 down, 0 unchanged, 1 up */
    private int highDelta;
    private int lowDelta;
    private int avgDelta;
    /** Prices from the previous refresh, for price-only total comparisons */
    private long prevHighPrice;
    private long prevLowPrice;
    private long prevAvgPrice;
    /** True once deltas have been computed (requires two completed price loads) */
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

    /** True if any price data has been loaded. */
    public boolean hasPrices()
    {
        return highPrice > 0 || lowPrice > 0;
    }
}
