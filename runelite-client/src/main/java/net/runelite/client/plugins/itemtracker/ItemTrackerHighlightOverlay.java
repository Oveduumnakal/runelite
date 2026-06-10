package net.runelite.client.plugins.itemtracker;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Outlines tracked items in the inventory and bank using the item's clickbox bounds. */
public class ItemTrackerHighlightOverlay extends WidgetItemOverlay
{
    private final ItemTrackerPlugin plugin;
    private final ItemTrackerConfig config;
    private final ItemManager itemManager;

    @Inject
    ItemTrackerHighlightOverlay(ItemTrackerPlugin plugin, ItemTrackerConfig config, ItemManager itemManager)
    {
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        showOnInventory();
        showOnBank();
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        if (!config.highlightMode().invBank()
                || !plugin.isTracked(itemManager.canonicalize(itemId)))
        {
            return;
        }

        Rectangle bounds = widgetItem.getCanvasBounds();
        if (bounds != null)
        {
            BufferedImage outline = itemManager.getItemOutline(
                    itemId, widgetItem.getQuantity(), config.highlightColor());

            Composite original = graphics.getComposite();
            graphics.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, plugin.breathingAlpha()));
            graphics.drawImage(outline, bounds.x, bounds.y, null);
            graphics.setComposite(original);
        }
    }
}
