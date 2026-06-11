/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.itemtracker;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;

@ConfigGroup(ItemTrackerConfig.GROUP)
public interface ItemTrackerConfig extends Config
{
	String GROUP = "itemtracker";

	String KEY_TRACKED_ITEMS = "trackedItemIds";
	String KEY_PRICE_DISPLAY = "priceDisplay";
	String KEY_GE_REFRESH_RATE = "geRefreshRate";
	String KEY_ITEM_VALUE_FORMAT = "itemValueFormat";
	String KEY_TOTAL_VALUE_FORMAT = "totalValueFormat";
	String KEY_NOTIFY_ON_VALUE_THRESHOLD = "notifyOnValueThreshold";
	String KEY_VALUE_THRESHOLD = "valueThreshold";

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
			keyName = KEY_PRICE_DISPLAY,
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
			keyName = KEY_GE_REFRESH_RATE,
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
			keyName = KEY_ITEM_VALUE_FORMAT,
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
			keyName = KEY_TOTAL_VALUE_FORMAT,
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
			keyName = KEY_NOTIFY_ON_VALUE_THRESHOLD,
			name = "Enable Notification",
			description = "Send a notification when the total average value exceeds the threshold",
			section = notificationsSection,
			position = 0
	)
	default Notification notifyOnValueThreshold()
	{
		return Notification.OFF;
	}

	@Range(min = 0)
	@ConfigItem(
			keyName = KEY_VALUE_THRESHOLD,
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
