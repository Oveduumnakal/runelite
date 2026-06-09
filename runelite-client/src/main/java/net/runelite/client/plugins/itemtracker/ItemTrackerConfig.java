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
            name = "Item Value Format",
            description = "How to display the value of individual tracked items"
    )
    default ValueFormat itemValueFormat()
    {
        return ValueFormat.ABBREVIATED;
    }

    @ConfigItem(
            keyName = "totalValueFormat",
            name = "Total Value Format",
            description = "How to display the running total value"
    )
    default ValueFormat totalValueFormat()
    {
        return ValueFormat.ABBREVIATED;
    }

    @Range(min = 60)
    @ConfigItem(
            keyName = "geRefreshRate",
            name = "GE Price Refresh Rate (seconds)",
            description = "How often to refresh GE prices. Minimum 60 seconds."
    )
    default int geRefreshRate()
    {
        return 60;
    }
}