package net.runelite.client.plugins.itemtracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("itemtracker")
public interface ItemTrackerConfig extends Config
{
    @ConfigSection(
            name = "Prices",
            description = "Price display and refresh settings",
            position = 0
    )
    String pricesSection = "prices";

    @ConfigSection(
            name = "Formatting",
            description = "How item and total values are formatted",
            position = 1
    )
    String formattingSection = "formatting";

    @ConfigSection(
            name = "Notifications",
            description = "Value threshold notification settings",
            position = 2
    )
    String notificationsSection = "notifications";

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
            keyName = "priceDisplay",
            name = "Display",
            description = "Which prices to show per item and in the totals",
            section = pricesSection,
            position = 0
    )
    default PriceDisplay priceDisplay()
    {
        return PriceDisplay.BOTH;
    }

    @Range(min = 30)
    @ConfigItem(
            keyName = "geRefreshRate",
            name = "Refresh (Seconds)",
            description = "How often to refresh GE prices. Minimum 30 seconds.",
            section = pricesSection,
            position = 1
    )
    default int geRefreshRate()
    {
        return 60;
    }

    @ConfigItem(
            keyName = "itemValueFormat",
            name = "Item Price",
            description = "How to display the value of individual tracked items",
            section = formattingSection,
            position = 0
    )
    default ValueFormat itemValueFormat()
    {
        return ValueFormat.ABBREVIATED;
    }

    @ConfigItem(
            keyName = "totalValueFormat",
            name = "Total Price",
            description = "How to display the running total value",
            section = formattingSection,
            position = 1
    )
    default ValueFormat totalValueFormat()
    {
        return ValueFormat.ABBREVIATED;
    }

    @ConfigItem(
            keyName = "notifyOnValueThreshold",
            name = "Enable Notification",
            description = "Send a notification when the total average value exceeds the threshold",
            section = notificationsSection,
            position = 0
    )
    default boolean notifyOnValueThreshold()
    {
        return false;
    }

    @Range(min = 0)
    @ConfigItem(
            keyName = "valueThreshold",
            name = "Threshold",
            description = "Total average value (gp) that triggers the notification",
            section = notificationsSection,
            position = 1
    )
    default int valueThreshold()
    {
        return 0;
    }
}
