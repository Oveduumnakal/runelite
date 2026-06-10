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

    private static final ImmutableSet<Integer> TRACKED_CONTAINERS = ImmutableSet.of(
            InventoryID.INV,
            InventoryID.WORN,
            InventoryID.BANK,
            InventoryID.LOOTING_BAG,
            InventoryID.SEED_BOX,
            InventoryID.SEED_VAULT,
            InventoryID.TACKLE_BOX,
            InventoryID.FORESTRY_KIT,
            InventoryID.HUNTSMANS_KIT,
            InventoryID.BARBARIAN_KNAPSACK
    );

    private final Map<Integer, TrackedItem> trackedItems = new LinkedHashMap<>();

    private final Map<Integer, Map<Integer, Integer>> containerCounts = new HashMap<>();

    private final Map<Integer, Integer> runePouchCounts = new HashMap<>();

    private final Map<TileItem, Tile> groundItems = new HashMap<>();

    private ItemTrackerPanel panel;
    private NavigationButton navButton;
    private ScheduledFuture<?> priceRefreshTask;
    private Instant lastPriceRefresh = null;

    private boolean valueThresholdNotified = false;

    private boolean valueThresholdPrimed = false;

    private Instant lastThresholdNotification = null;
    private static final long THRESHOLD_NOTIFY_COOLDOWN_SECONDS = 10;

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
            refreshGePrices();
        });
    }

    private void removeTrackedItem(int itemId)
    {
        trackedItems.remove(itemId);
        persistTrackedItems();
        refreshPanel();
    }

    private void refreshGePrices()
    {
        executor.execute(() ->
        {
            Map<Integer, WikiRealtimePriceClient.ItemPrices> all = wikiPriceClient.fetchAll();

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
            return;
        }
    }

    private int getItemIdFromMenuEntry(MenuEntry entry)
    {
        switch (entry.getType())
        {
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
        persistTrackedItems();
    }

    private void syncQuantitiesForItem(TrackedItem tracked)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

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

        syncRunePouch();

        int total = runePouchCounts.getOrDefault(tracked.getItemId(), 0);
        for (Map<Integer, Integer> c : containerCounts.values())
        {
            total += c.getOrDefault(tracked.getItemId(), 0);
        }
        tracked.setQuantity(total);
    }

    boolean isTracked(int itemId)
    {
        return trackedItems.containsKey(itemId);
    }

    private static final long GLOW_PERIOD_SLOW_MS = 2000;
    private static final long GLOW_PERIOD_MEDIUM_MS = 1500;
    private static final long GLOW_PERIOD_FAST_MS = 1000;
    private static final float GLOW_MIN_ALPHA = 0.2f;
    private static final float GLOW_MAX_ALPHA = 1f;

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
        double wave = (Math.sin(phase * 2 * Math.PI) + 1) / 2;
        return GLOW_MIN_ALPHA + (GLOW_MAX_ALPHA - GLOW_MIN_ALPHA) * (float) wave;
    }

    Map<TileItem, Tile> getGroundItems()
    {
        return groundItems;
    }

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

    private void checkValueThreshold()
    {
        if (!config.notifyOnValueThreshold().isEnabled())
        {
            return;
        }

        long threshold = config.valueThreshold();
        if (threshold <= 0)
        {
            return;
        }

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
                Instant now = Instant.now();
                if (lastThresholdNotification != null
                        && ChronoUnit.SECONDS.between(lastThresholdNotification, now) < THRESHOLD_NOTIFY_COOLDOWN_SECONDS)
                {
                    return;
                }

                valueThresholdNotified = true;
                lastThresholdNotification = now;
                notifier.notify(config.notifyOnValueThreshold(),
                        "Total value of tracked items exceeded " + abbreviateGp(threshold) + " gp");
            }
        }
        else
        {
            valueThresholdNotified = false;
        }
    }

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
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + suffix;
    }

}
