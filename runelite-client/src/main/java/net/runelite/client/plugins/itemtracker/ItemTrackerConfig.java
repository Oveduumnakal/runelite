package net.runelite.client.plugins.itemtracker;

import java.awt.Color;
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

    @ConfigSection(
            name = "Highlighting",
            description = "Tracked item highlighting settings",
            position = 3
    )
    String highlightingSection = "highlighting";

    @ConfigSection(
            name = "Miscellaneous",
            description = "Miscellaneous settings",
            position = 4
    )
    String miscellaneousSection = "miscellaneous";

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

    @ConfigItem(
            keyName = "highlightMode",
            name = "Highlight Tracked Items",
            description = "Where to outline tracked items",
            section = highlightingSection,
            position = 0
    )
    default HighlightMode highlightMode()
    {
        return HighlightMode.GROUND;
    }

    @ConfigItem(
            keyName = "highlightColor",
            name = "Highlight Color",
            description = "Color used to outline tracked items",
            section = highlightingSection,
            position = 1
    )
    default Color highlightColor()
    {
        return new Color(0xfb, 0xcd, 0x2b);
    }

    @ConfigItem(
            keyName = "glowEffect",
            name = "Glow Effect",
            description = "Speed of the highlight's breathing/glow effect",
            section = highlightingSection,
            position = 2
    )
    default GlowSpeed glowEffect()
    {
        return GlowSpeed.MEDIUM;
    }

    @ConfigItem(
            keyName = "menuTrackItem",
            name = "Track Item Menu Option",
            description = "Add a right-click menu option to track/untrack items on the ground, in the bank, or in the inventory",
            section = miscellaneousSection,
            position = 0
    )
    default boolean menuTrackItem()
    {
        return true;
    }

    @ConfigItem(
            keyName = "priceChangeIndicator",
            name = "Price Change Indicator",
            description = "Pulse an indicator next to prices when they refresh: All also shows unchanged prices, Change only up/down movements",
            section = miscellaneousSection,
            position = 1
    )
    default PriceIndicatorMode priceChangeIndicator()
    {
        return PriceIndicatorMode.CHANGE;
    }

    @ConfigItem(
            keyName = "trackItemColor",
            name = "Track Item Color",
            description = "Color of the \"Track Item\" context menu entry",
            section = miscellaneousSection,
            position = 2
    )
    default Color trackItemColor()
    {
        return new Color(0xd8, 0xfb, 0xd4);
    }

    @ConfigItem(
            keyName = "stopTrackingColor",
            name = "Stop Tracking Color",
            description = "Color of the \"Stop Tracking\" context menu entry",
            section = miscellaneousSection,
            position = 3
    )
    default Color stopTrackingColor()
    {
        return new Color(0xfb, 0xd4, 0xd4);
    }
}
