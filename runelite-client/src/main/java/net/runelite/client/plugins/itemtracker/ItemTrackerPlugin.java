package net.runelite.client.plugins.itemtracker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import com.google.common.collect.ImmutableSet;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.config.Notification;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    @Inject
    private Notifier notifier;

    @Inject
    private RuneLiteConfig runeLiteConfig;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ItemTrackerHighlightOverlay highlightOverlay;

    @Inject
    private ItemTrackerGroundOverlay groundOverlay;

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

    // Items currently on the ground, for the ground highlight overlay
    private final Map<TileItem, Tile> groundItems = new HashMap<>();

    private ItemTrackerPanel panel;
    private NavigationButton navButton;
    private ScheduledFuture<?> priceRefreshTask;
    private Instant lastPriceRefresh = null;

    // Latch for the value threshold notification: set when the total avg value first
    // exceeds the threshold, cleared when it falls back below so it can fire again.
    private boolean valueThresholdNotified = false;

    // False until the first threshold evaluation after startup. The first evaluation
    // only arms the latch from the current state — without notifying — so a value that
    // was already above the threshold last session doesn't re-notify on every startup.
    private boolean valueThresholdPrimed = false;

    // When the threshold notification last fired; used to rate-limit re-fires.
    private Instant lastThresholdNotification = null;
    private static final long THRESHOLD_NOTIFY_COOLDOWN_SECONDS = 10;

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
                config::priceDisplay,
                config::geRefreshRate
        );

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

        navButton = NavigationButton.builder()
                .tooltip("Item Tracker")
                .icon(icon)
                .priority(6)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
        overlayManager.add(highlightOverlay);
        overlayManager.add(groundOverlay);
        loadPersistedItems();
        scheduleRefresh();
    }

    @Override
    protected void shutDown() throws Exception
    {
        clientToolbar.removeNavigation(navButton);
        overlayManager.remove(highlightOverlay);
        overlayManager.remove(groundOverlay);
        groundItems.clear();
        if (priceRefreshTask != null)
        {
            priceRefreshTask.cancel(false);
            priceRefreshTask = null;
        }
        trackedItems.clear();
        containerCounts.clear();
        runePouchCounts.clear();
        lastPriceRefresh = null;
        valueThresholdNotified = false;
        valueThresholdPrimed = false;
        lastThresholdNotification = null;
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

        int rate = Math.max(30, config.geRefreshRate());
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
                // "itemId:quantity", or just "itemId" from older versions
                String[] fields = part.split(":");
                int itemId = Integer.parseInt(fields[0].trim());
                int quantity = fields.length > 1 ? Integer.parseInt(fields[1].trim()) : 0;
                addTrackedItem(itemId, quantity);
            }
            catch (NumberFormatException e)
            {
                log.warn("Invalid tracked item entry in config: {}", part);
            }
        }
    }

    private void persistTrackedItems()
    {
        String ids = trackedItems.values().stream()
                .map(item -> item.getItemId() + ":" + item.getQuantity())
                .collect(Collectors.joining(","));
        config.setTrackedItemIds(ids);
    }

    // -----------------------------------------------------------------------
    // Add / remove items
    // -----------------------------------------------------------------------

    private void addTrackedItem(int itemId)
    {
        addTrackedItem(itemId, 0);
    }

    private void addTrackedItem(int itemId, int initialQuantity)
    {
        if (trackedItems.containsKey(itemId))
        {
            return;
        }

        clientThread.invokeLater(() ->
        {
            var composition = itemManager.getItemComposition(itemId);
            TrackedItem tracked = new TrackedItem(itemId, composition.getName());
            tracked.setTradeable(composition.isTradeable());
            tracked.setQuantity(initialQuantity);
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

            // An empty map means the fetch itself failed (network/API error)
            boolean fetchFailed = all.isEmpty();

            for (TrackedItem item : trackedItems.values())
            {
                WikiRealtimePriceClient.ItemPrices prices = all.get(item.getItemId());
                if (prices != null)
                {
                    if (item.hasPrices())
                    {
                        item.setHighDelta(Long.compare(prices.getHigh(), item.getHighPrice()));
                        item.setLowDelta(Long.compare(prices.getLow(), item.getLowPrice()));
                        item.setAvgDelta(Long.compare(prices.avg(), item.getAvgPrice()));
                        item.setPrevHighPrice(item.getHighPrice());
                        item.setPrevLowPrice(item.getLowPrice());
                        item.setPrevAvgPrice(item.getAvgPrice());
                        item.setHasDeltas(true);
                    }
                    item.setHighPrice(prices.getHigh());
                    item.setLowPrice(prices.getLow());
                    item.setAvgPrice(prices.avg());
                    item.setPriceLoadFailed(false);
                }
                else if (!item.hasPrices() && item.isTradeable())
                {
                    // Fetch failed, or the item is missing from the price data;
                    // keep any previously loaded prices, otherwise flag the failure
                    item.setPriceLoadFailed(true);
                }
            }

            if (fetchFailed)
            {
                refreshPanel();
                return;
            }

            lastPriceRefresh = Instant.now();
            refreshPanel(true);
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
            case "notifyOnValueThreshold":
            case "valueThreshold":
                valueThresholdNotified = false;
                checkValueThreshold();
                break;
        }
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event)
    {
        if (!config.menuTrackItem())
        {
            return;
        }

        final MenuEntry[] entries = event.getMenuEntries();
        for (int idx = entries.length - 1; idx >= 0; --idx)
        {
            final MenuEntry entry = entries[idx];
            int itemId = getItemIdFromMenuEntry(entry);
            if (itemId <= 0)
            {
                continue;
            }

            final int canonicalId = itemManager.canonicalize(itemId);
            final boolean tracked = trackedItems.containsKey(canonicalId);

            // Index 0 is the bottom of the menu ("Cancel"); 1 puts it right above it
            client.createMenuEntry(1)
                    .setOption(tracked
                            ? ColorUtil.prependColorTag("Stop Tracking", config.stopTrackingColor())
                            : ColorUtil.prependColorTag("Track Item", config.trackItemColor()))
                    .setTarget(entry.getTarget())
                    .setType(MenuAction.RUNELITE)
                    .onClick(e ->
                    {
                        if (tracked)
                        {
                            removeTrackedItem(canonicalId);
                        }
                        else
                        {
                            addTrackedItem(canonicalId);
                        }
                    });
            return; // one option per menu
        }
    }

    /**
     * Resolves the item ID a menu entry refers to, for ground items and for
     * items in the inventory or bank. Returns -1 for anything else.
     */
    private int getItemIdFromMenuEntry(MenuEntry entry)
    {
        switch (entry.getType())
        {
            // Ground items: the identifier is the item ID
            case GROUND_ITEM_FIRST_OPTION:
            case GROUND_ITEM_SECOND_OPTION:
            case GROUND_ITEM_THIRD_OPTION:
            case GROUND_ITEM_FOURTH_OPTION:
            case GROUND_ITEM_FIFTH_OPTION:
            case EXAMINE_ITEM_GROUND:
                return entry.getIdentifier();
            default:
                break;
        }

        Widget w = entry.getWidget();
        if (w == null)
        {
            return -1;
        }

        int interfaceId = WidgetUtil.componentToInterface(w.getId());
        if (interfaceId == InterfaceID.INVENTORY
                || interfaceId == InterfaceID.BANKMAIN
                || interfaceId == InterfaceID.BANKSIDE)
        {
            return w.getItemId();
        }
        return -1;
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

    /**
     * Moves "Take" entries for highlighted (tracked) ground items to the top of the
     * menu, so they take priority in a stack of items. Non-tracked items keep their
     * standard order relative to each other.
     */
    @Subscribe
    public void onClientTick(ClientTick event)
    {
        if (!config.highlightMode().ground() || client.isMenuOpen())
        {
            return;
        }

        final MenuEntry[] entries = client.getMenuEntries();
        final List<MenuEntry> normal = new ArrayList<>(entries.length);
        final List<MenuEntry> trackedTakes = new ArrayList<>();

        for (MenuEntry entry : entries)
        {
            if (entry.getType() == MenuAction.GROUND_ITEM_THIRD_OPTION
                    && isTracked(itemManager.canonicalize(entry.getIdentifier())))
            {
                trackedTakes.add(entry);
            }
            else
            {
                normal.add(entry);
            }
        }

        if (trackedTakes.isEmpty())
        {
            return;
        }

        // Entries later in the array appear higher in the menu and become the
        // default left-click action, so tracked Takes go last.
        normal.addAll(trackedTakes);
        client.setMenuEntries(normal.toArray(new MenuEntry[0]));
    }

    @Subscribe
    public void onItemSpawned(ItemSpawned event)
    {
        groundItems.put(event.getItem(), event.getTile());
    }

    @Subscribe
    public void onItemDespawned(ItemDespawned event)
    {
        groundItems.remove(event.getItem());
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        // Tiles are invalidated on scene load
        if (event.getGameState() == GameState.LOADING)
        {
            groundItems.clear();
        }
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
        persistTrackedItems(); // keep persisted quantities current for the next session
    }

    private void syncQuantitiesForItem(TrackedItem tracked)
    {
        // While logged out no containers are available; keep the persisted
        // quantity instead of overwriting it with zero.
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

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
    // Overlay accessors
    // -----------------------------------------------------------------------

    /** True if the (canonical) item ID is currently tracked. */
    boolean isTracked(int itemId)
    {
        return trackedItems.containsKey(itemId);
    }

    private static final long GLOW_PERIOD_SLOW_MS = 2000;
    private static final long GLOW_PERIOD_MEDIUM_MS = 1500;
    private static final long GLOW_PERIOD_FAST_MS = 1000;
    private static final float GLOW_MIN_ALPHA = 0.2f;
    private static final float GLOW_MAX_ALPHA = 1f;

    /**
     * Opacity for the highlight overlays, oscillating smoothly over time for a
     * glow/breathing effect. Shared so all highlights pulse in sync. Returns
     * full opacity when the glow effect is off.
     */
    float breathingAlpha()
    {
        long period;
        switch (config.glowEffect())
        {
            case SLOW:
                period = GLOW_PERIOD_SLOW_MS;
                break;
            case MEDIUM:
                period = GLOW_PERIOD_MEDIUM_MS;
                break;
            case FAST:
                period = GLOW_PERIOD_FAST_MS;
                break;
            default:
                return GLOW_MAX_ALPHA;
        }

        double phase = (System.currentTimeMillis() % period) / (double) period;
        double wave = (Math.sin(phase * 2 * Math.PI) + 1) / 2; // 0..1
        return GLOW_MIN_ALPHA + (GLOW_MAX_ALPHA - GLOW_MIN_ALPHA) * (float) wave;
    }

    /** Items currently on the ground, for the ground highlight overlay. */
    Map<TileItem, Tile> getGroundItems()
    {
        return groundItems;
    }

    // -----------------------------------------------------------------------
    // Panel refresh
    // -----------------------------------------------------------------------

    private void refreshPanel()
    {
        refreshPanel(false);
    }

    private void refreshPanel(boolean pricesUpdated)
    {
        checkValueThreshold();
        final Instant refresh = lastPriceRefresh;
        final PriceIndicatorMode indicatorMode = pricesUpdated
                ? config.priceChangeIndicator()
                : PriceIndicatorMode.OFF;
        SwingUtilities.invokeLater(() ->
                panel.rebuild(new ArrayList<>(trackedItems.values()), refresh, indicatorMode)
        );
    }

    // -----------------------------------------------------------------------
    // Value threshold notification
    // -----------------------------------------------------------------------

    /**
     * Notifies when the total avg value first exceeds the configured threshold.
     * Latched: won't fire again until the value drops below the threshold and
     * then exceeds it once more.
     */
    private void checkValueThreshold()
    {
        if (!config.notifyOnValueThreshold())
        {
            return;
        }

        long threshold = config.valueThreshold();
        if (threshold <= 0)
        {
            return;
        }

        // Don't evaluate until prices have loaded, otherwise a partial total
        // could falsely reset (or trigger) the latch.
        boolean hasPrices = trackedItems.values().stream().anyMatch(TrackedItem::hasPrices);
        if (!hasPrices)
        {
            return;
        }

        long totalAvg = trackedItems.values().stream()
                .mapToLong(TrackedItem::getAvgValue)
                .sum();

        if (!valueThresholdPrimed)
        {
            valueThresholdPrimed = true;
            valueThresholdNotified = totalAvg > threshold;
            return;
        }

        if (totalAvg > threshold)
        {
            if (!valueThresholdNotified)
            {
                // Rate limit: skip (without latching) if we notified in the last 10s,
                // so a still-exceeding value notifies once the cooldown expires.
                Instant now = Instant.now();
                if (lastThresholdNotification != null
                        && ChronoUnit.SECONDS.between(lastThresholdNotification, now) < THRESHOLD_NOTIFY_COOLDOWN_SECONDS)
                {
                    return;
                }

                valueThresholdNotified = true;
                lastThresholdNotification = now;
                notifier.notify(buildSendWhenFocusedNotification(),
                        "Total value of tracked items exceeded " + abbreviateGp(threshold) + " gp");
            }
        }
        else
        {
            valueThresholdNotified = false;
        }
    }

    /**
     * Mirrors {@code Notifier.defaultNotification} (which is private): uses the user's
     * global RuneLite notification settings, but forces sendWhenFocused so the threshold
     * notification fires even while the client window is focused.
     */
    private Notification buildSendWhenFocusedNotification()
    {
        return new Notification(true, true, true,
                runeLiteConfig.enableTrayNotifications(), java.awt.TrayIcon.MessageType.NONE,
                runeLiteConfig.notificationRequestFocus(),
                runeLiteConfig.notificationSound(), null,
                runeLiteConfig.notificationVolume(), runeLiteConfig.notificationTimeout(),
                runeLiteConfig.enableGameMessageNotification(), runeLiteConfig.flashNotification(),
                runeLiteConfig.notificationFlashColor(),
                true /* sendWhenFocused */);
    }

    /** Formats a gp value abbreviated (k/m/b), dropping unnecessary decimals: 50k, 1.25m, 2b. */
    private static String abbreviateGp(long value)
    {
        if (value < 1_000)
        {
            return String.valueOf(value);
        }

        double scaled;
        String suffix;
        if (value >= 1_000_000_000)
        {
            scaled = value / 1_000_000_000.0;
            suffix = "b";
        }
        else if (value >= 1_000_000)
        {
            scaled = value / 1_000_000.0;
            suffix = "m";
        }
        else
        {
            scaled = value / 1_000.0;
            suffix = "k";
        }

        String s = String.format("%.2f", scaled);
        // Trim trailing zeros and a dangling decimal point: "50.00" -> "50", "1.50" -> "1.5"
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + suffix;
    }

}