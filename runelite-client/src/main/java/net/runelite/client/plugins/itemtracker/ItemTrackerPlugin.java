package net.runelite.client.plugins.itemtracker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = "Item Tracker",
        description = "Track item quantities across your inventory and bank with live GE prices",
        tags = {"items", "bank", "inventory", "price", "ge", "tracker"}
)
public class ItemTrackerPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ItemTrackerConfig config;

    @Inject
    private ScheduledExecutorService executor;

    // itemId -> TrackedItem
    private final Map<Integer, TrackedItem> trackedItems = new LinkedHashMap<>();

    // Last known container counts
    private final Map<Integer, Integer> lastInventoryCount = new HashMap<>();
    private final Map<Integer, Integer> lastBankCount = new HashMap<>();

    private ItemTrackerPanel panel;
    private NavigationButton navButton;
    private ScheduledFuture<?> priceRefreshTask;
    private Instant lastPriceRefresh = null;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    protected void startUp() throws Exception
    {
        panel = new ItemTrackerPanel(
                itemManager,
                this::addTrackedItem,
                this::removeTrackedItem,
                config::itemValueFormat,
                config::totalValueFormat
        );

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

        navButton = NavigationButton.builder()
                .tooltip("Item Tracker")
                .icon(icon)
                .priority(6)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
        loadPersistedItems();
        scheduleRefresh();
    }

    @Override
    protected void shutDown() throws Exception
    {
        clientToolbar.removeNavigation(navButton);
        if (priceRefreshTask != null)
        {
            priceRefreshTask.cancel(false);
            priceRefreshTask = null;
        }
        trackedItems.clear();
        lastInventoryCount.clear();
        lastBankCount.clear();
        lastPriceRefresh = null;
    }

    @Provides
    ItemTrackerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ItemTrackerConfig.class);
    }

    // -----------------------------------------------------------------------
    // Scheduling
    // -----------------------------------------------------------------------

    /**
     * Schedules (or reschedules) the GE price refresh task using the current config rate.
     * Called on startup and can be called again if the config changes.
     */
    private void scheduleRefresh()
    {
        if (priceRefreshTask != null)
        {
            priceRefreshTask.cancel(false);
        }

        int rate = Math.max(60, config.geRefreshRate());
        priceRefreshTask = executor.scheduleAtFixedRate(
                this::refreshGePrices, 0, rate, TimeUnit.SECONDS
        );
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    private void loadPersistedItems()
    {
        String saved = config.trackedItemIds();
        if (saved == null || saved.trim().isEmpty())
        {
            return;
        }

        for (String part : saved.split(","))
        {
            part = part.trim();
            if (part.isEmpty()) continue;
            try
            {
                int itemId = Integer.parseInt(part);
                addTrackedItem(itemId);
            }
            catch (NumberFormatException e)
            {
                log.warn("Invalid tracked item ID in config: {}", part);
            }
        }
    }

    private void persistTrackedItems()
    {
        String ids = trackedItems.keySet().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        config.setTrackedItemIds(ids);
    }

    // -----------------------------------------------------------------------
    // Add / remove items
    // -----------------------------------------------------------------------

    private void addTrackedItem(int itemId)
    {
        if (trackedItems.containsKey(itemId))
        {
            return;
        }

        clientThread.invokeLater(() ->
        {
            String name = itemManager.getItemComposition(itemId).getName();
            TrackedItem tracked = new TrackedItem(itemId, name);
            trackedItems.put(itemId, tracked);

            syncQuantitiesForItem(tracked);
            persistTrackedItems();
            refreshGePrices();
            refreshPanel();
        });
    }

    private void removeTrackedItem(int itemId)
    {
        trackedItems.remove(itemId);
        persistTrackedItems();
        refreshPanel();
    }

    // -----------------------------------------------------------------------
    // GE price fetching
    // -----------------------------------------------------------------------

    private void refreshGePrices()
    {
        clientThread.invokeLater(() ->
        {
            for (TrackedItem item : trackedItems.values())
            {
                long price = itemManager.getItemPrice(item.getItemId());
                item.setGePrice(price);
            }
            lastPriceRefresh = Instant.now();
            refreshPanel();
        });
    }

    // -----------------------------------------------------------------------
    // Inventory / bank event handling
    // -----------------------------------------------------------------------

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        int containerId = event.getContainerId();

        if (containerId == 93)
        {
            updateCountsFromContainer(event.getItemContainer(), lastInventoryCount);
        }
        else if (containerId == 95)
        {
            updateCountsFromContainer(event.getItemContainer(), lastBankCount);
        }
        else
        {
            return;
        }

        for (TrackedItem item : trackedItems.values())
        {
            int inv = lastInventoryCount.getOrDefault(item.getItemId(), 0);
            int bank = lastBankCount.getOrDefault(item.getItemId(), 0);
            item.setQuantity(inv + bank);
        }

        refreshPanel();
    }

    private void updateCountsFromContainer(ItemContainer container, Map<Integer, Integer> countMap)
    {
        countMap.clear();
        if (container == null) return;

        for (Item item : container.getItems())
        {
            if (item.getId() <= 0) continue;
            countMap.merge(item.getId(), item.getQuantity(), Integer::sum);
        }
    }

    private void syncQuantitiesForItem(TrackedItem tracked)
    {
        int total = 0;

        ItemContainer inv = client.getItemContainer(93);
        if (inv != null)
        {
            for (Item item : inv.getItems())
            {
                if (item.getId() == tracked.getItemId())
                {
                    total += item.getQuantity();
                }
            }
        }

        ItemContainer bank = client.getItemContainer(95);
        if (bank != null)
        {
            for (Item item : bank.getItems())
            {
                if (item.getId() == tracked.getItemId())
                {
                    total += item.getQuantity();
                }
            }
        }

        tracked.setQuantity(total);
    }

    // -----------------------------------------------------------------------
    // Panel refresh
    // -----------------------------------------------------------------------

    private void refreshPanel()
    {
        final Instant refresh = lastPriceRefresh;
        SwingUtilities.invokeLater(() ->
                panel.rebuild(new ArrayList<>(trackedItems.values()), refresh)
        );
    }
}