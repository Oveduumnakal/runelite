package net.runelite.client.plugins.itemtracker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import com.google.common.collect.ImmutableSet;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.events.ConfigChanged;
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

    @Inject
    private WikiRealtimePriceClient wikiPriceClient;

    private static final int[] RUNE_POUCH_TYPE_VARBITS = {
            VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
            VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
    };
    private static final int[] RUNE_POUCH_QUANTITY_VARBITS = {
            VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
            VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
    };
    private static final ImmutableSet<Integer> RUNE_POUCH_VARBITS;
    static
    {
        ImmutableSet.Builder<Integer> b = ImmutableSet.builder();
        for (int v : RUNE_POUCH_TYPE_VARBITS) b.add(v);
        for (int v : RUNE_POUCH_QUANTITY_VARBITS) b.add(v);
        RUNE_POUCH_VARBITS = b.build();
    }

    /**
     * All item containers we scan for tracked items.
     * Note: herb sack, coal bag, gem bag, fur/meat pouch, and bolt pouch
     * store their contents via VarBits with no named IDs in the RuneLite API.
     * The rune pouch is handled separately via {@link #syncRunePouch()}.
     */
    private static final ImmutableSet<Integer> TRACKED_CONTAINERS = ImmutableSet.of(
            InventoryID.INV,              // main inventory
            InventoryID.WORN,             // equipped items
            InventoryID.BANK,             // bank
            InventoryID.LOOTING_BAG,      // looting bag
            InventoryID.SEED_BOX,         // seed box
            InventoryID.SEED_VAULT,       // seed vault
            InventoryID.TACKLE_BOX,       // tackle box
            InventoryID.FORESTRY_KIT,     // forestry kit / log basket
            InventoryID.HUNTSMANS_KIT,    // huntsman's kit
            InventoryID.BARBARIAN_KNAPSACK // barbarian knapsack
    );

    // itemId -> TrackedItem
    private final Map<Integer, TrackedItem> trackedItems = new LinkedHashMap<>();

    // containerId -> (itemId -> quantity)  — one entry per TRACKED_CONTAINERS
    private final Map<Integer, Map<Integer, Integer>> containerCounts = new HashMap<>();

    // itemId -> quantity for rune pouch contents (read from VarBits)
    private final Map<Integer, Integer> runePouchCounts = new HashMap<>();

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
                config::totalValueFormat,
                config::priceDisplay
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
        containerCounts.clear();
        runePouchCounts.clear();
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
            refreshPanel();
            refreshGePrices(); // async HTTP fetch, updates panel again when done
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
        // Runs on the executor thread (not the client thread) — HTTP is fine here
        executor.execute(() ->
        {
            Map<Integer, WikiRealtimePriceClient.ItemPrices> all = wikiPriceClient.fetchAll();

            for (TrackedItem item : trackedItems.values())
            {
                WikiRealtimePriceClient.ItemPrices prices = all.get(item.getItemId());
                if (prices != null)
                {
                    item.setHighPrice(prices.getHigh());
                    item.setLowPrice(prices.getLow());
                    item.setAvgPrice(prices.avg());
                }
            }

            lastPriceRefresh = Instant.now();
            refreshPanel();
        });
    }

    // -----------------------------------------------------------------------
    // Inventory / bank event handling
    // -----------------------------------------------------------------------

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"itemtracker".equals(event.getGroup()))
        {
            return;
        }

        switch (event.getKey())
        {
            case "itemValueFormat":
            case "totalValueFormat":
            case "priceDisplay":
                refreshPanel();
                break;
            case "geRefreshRate":
                scheduleRefresh();
                break;
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        int containerId = event.getContainerId();
        if (!TRACKED_CONTAINERS.contains(containerId))
        {
            return;
        }

        // Rebuild the count snapshot for this container
        Map<Integer, Integer> counts = containerCounts.computeIfAbsent(containerId, k -> new HashMap<>());
        counts.clear();
        ItemContainer container = event.getItemContainer();
        if (container != null)
        {
            for (Item item : container.getItems())
            {
                if (item.getId() > 0)
                {
                    counts.merge(item.getId(), item.getQuantity(), Integer::sum);
                }
            }
        }

        recomputeAllQuantities();
        refreshPanel();
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (RUNE_POUCH_VARBITS.contains(event.getVarbitId()))
        {
            syncRunePouch();
            recomputeAllQuantities();
            refreshPanel();
        }
    }

    /** Reads all 6 rune pouch slots from VarBits and rebuilds {@link #runePouchCounts}. Must be on client thread. */
    private void syncRunePouch()
    {
        runePouchCounts.clear();
        EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
        for (int i = 0; i < RUNE_POUCH_TYPE_VARBITS.length; i++)
        {
            int typeId = client.getVarbitValue(RUNE_POUCH_TYPE_VARBITS[i]);
            int qty    = client.getVarbitValue(RUNE_POUCH_QUANTITY_VARBITS[i]);
            if (typeId == 0 || qty <= 0)
            {
                continue;
            }
            int itemId = runeEnum.getIntValue(typeId);
            runePouchCounts.merge(itemId, qty, Integer::sum);
        }
    }

    /** Recomputes quantities for all tracked items from all container + rune pouch snapshots. */
    private void recomputeAllQuantities()
    {
        for (TrackedItem tracked : trackedItems.values())
        {
            int total = runePouchCounts.getOrDefault(tracked.getItemId(), 0);
            for (Map<Integer, Integer> c : containerCounts.values())
            {
                total += c.getOrDefault(tracked.getItemId(), 0);
            }
            tracked.setQuantity(total);
        }
    }

    private void syncQuantitiesForItem(TrackedItem tracked)
    {
        // Snapshot all currently loaded item containers
        for (int containerId : TRACKED_CONTAINERS)
        {
            ItemContainer container = client.getItemContainer(containerId);
            if (container == null)
            {
                continue;
            }

            Map<Integer, Integer> counts = containerCounts.computeIfAbsent(containerId, k -> new HashMap<>());
            counts.clear();
            for (Item item : container.getItems())
            {
                if (item.getId() > 0)
                {
                    counts.merge(item.getId(), item.getQuantity(), Integer::sum);
                }
            }
        }

        // Snapshot rune pouch
        syncRunePouch();

        // Sum across all sources for this item
        int total = runePouchCounts.getOrDefault(tracked.getItemId(), 0);
        for (Map<Integer, Integer> c : containerCounts.values())
        {
            total += c.getOrDefault(tracked.getItemId(), 0);
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