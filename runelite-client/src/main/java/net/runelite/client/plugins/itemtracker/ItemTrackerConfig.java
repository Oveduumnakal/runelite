package net.runelite.client.plugins.itemtracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("itemtracker")
public interface ItemTrackerConfig extends Config
{
    @ConfigItem(
            keyName = "trackedItemIds",
            name = "Tracked Item IDs",
            description = "Comma-separated list of item IDs being tracked",
            hidden = true
    )
    default String trackedItemIds()
    {
        return "";
    }

    @ConfigItem(
            keyName = "trackedItemIds",
            name = "Tracked Item IDs",
            description = "Comma-separated list of item IDs being tracked"
    )
    void setTrackedItemIds(String ids);

    @ConfigItem(
            keyName = "itemValueFormat",
            name = "Price Format (Item)",
            description = "How to display the value of individual tracked items"
    )
    default ValueFormat itemValueFormat()
    {
        return ValueFormat.ABBREVIATED;
    }

    @ConfigItem(
            keyName = "totalValueFormat",
            name = "Price Format (Total)",
            description = "How to display the running total value"
    )
    default ValueFormat totalValueFormat()
    {
        return ValueFormat.ABBREVIATED;
    }

    @ConfigItem(
            keyName = "priceDisplay",
            name = "Price Display",
            description = "Which prices to show per item and in the totals"
    )
    default PriceDisplay priceDisplay()
    {
        return PriceDisplay.BOTH;
    }

    @Range(min = 60)
    @ConfigItem(
            keyName = "geRefreshRate",
            name = "Price Refresh (Seconds)",
            description = "How often to refresh GE prices. Minimum 60 seconds."
    )
    default int geRefreshRate()
    {
        return 60;
    }
}