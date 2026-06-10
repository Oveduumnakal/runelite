package net.runelite.client.plugins.itemtracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Outlines the clickbox of tracked items lying on the ground. */
public class ItemTrackerGroundOverlay extends Overlay
{
    private final Client client;
    private final ItemTrackerPlugin plugin;
    private final ItemTrackerConfig config;
    private final ItemManager itemManager;

    @Inject
    ItemTrackerGroundOverlay(Client client, ItemTrackerPlugin plugin, ItemTrackerConfig config, ItemManager itemManager)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.highlightMode().ground())
        {
            return null;
        }

        Color base = config.highlightColor();
        Color breathing = new Color(base.getRed(), base.getGreen(), base.getBlue(),
                Math.round(plugin.breathingAlpha() * 255));

        for (Map.Entry<TileItem, Tile> entry : plugin.getGroundItems().entrySet())
        {
            if (!plugin.isTracked(itemManager.canonicalize(entry.getKey().getId())))
            {
                continue;
            }

            Tile tile = entry.getValue();
            Shape poly = Perspective.getCanvasTilePoly(client, tile.getLocalLocation());
            if (poly != null)
            {
                graphics.setColor(breathing);
                graphics.draw(poly);
            }
        }
        return null;
    }
}
