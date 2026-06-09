package net.runelite.client.plugins.itemtracker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.http.api.item.ItemPrice;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Slf4j
public class ItemTrackerPanel extends PluginPanel
{
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private static final Color COLOR_HIGH = new Color(100, 220, 100);
    private static final Color COLOR_LOW  = new Color(220, 100, 100);
    private static final Color COLOR_AVG  = new Color(255, 200, 0);

    private final ItemManager itemManager;
    private final Consumer<Integer> onAddItem;
    private final Consumer<Integer> onRemoveItem;
    private final Supplier<ValueFormat> itemValueFormatSupplier;
    private final Supplier<ValueFormat> totalValueFormatSupplier;
    private final Supplier<PriceDisplay> priceDisplaySupplier;

    // Search area
    private final IconTextField searchField;
    private final JPanel searchResultsPanel;

    // Tracked items list
    private final JPanel trackedItemsPanel;

    // Totals
    private final JLabel totalHighLabel;
    private final JLabel totalLowLabel;
    private final JLabel totalAvgLabel;
    private final JLabel lastRefreshLabel;

    private volatile Instant lastPriceRefresh = null;
    private final Timer refreshAgeTimer;

    public ItemTrackerPanel(
            ItemManager itemManager,
            Consumer<Integer> onAddItem,
            Consumer<Integer> onRemoveItem,
            Supplier<ValueFormat> itemValueFormatSupplier,
            Supplier<ValueFormat> totalValueFormatSupplier,
            Supplier<PriceDisplay> priceDisplaySupplier)
    {
        this.itemManager = itemManager;
        this.onAddItem = onAddItem;
        this.onRemoveItem = onRemoveItem;
        this.itemValueFormatSupplier = itemValueFormatSupplier;
        this.totalValueFormatSupplier = totalValueFormatSupplier;
        this.priceDisplaySupplier = priceDisplaySupplier;

        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // --- Title ---
        JLabel title = new JLabel("Item Tracker");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setBorder(new EmptyBorder(0, 0, 4, 0));

        // --- Search results ---
        searchResultsPanel = new JPanel();
        searchResultsPanel.setLayout(new BoxLayout(searchResultsPanel, BoxLayout.Y_AXIS));
        searchResultsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchResultsPanel.setVisible(false);

        // --- Search field ---
        searchField = new IconTextField();
        searchField.setIcon(IconTextField.Icon.SEARCH);
        searchField.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchField.setMinimumSize(new Dimension(0, 30));
        searchField.addClearListener(() -> searchResultsPanel.setVisible(false));
        searchField.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e) { onSearch(searchField.getText()); }
            public void removeUpdate(DocumentEvent e) { onSearch(searchField.getText()); }
            public void changedUpdate(DocumentEvent e) { onSearch(searchField.getText()); }
        });

        // --- Tracked items panel ---
        trackedItemsPanel = new JPanel();
        trackedItemsPanel.setLayout(new BoxLayout(trackedItemsPanel, BoxLayout.Y_AXIS));
        trackedItemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel trackedLabel = new JLabel("Tracked Items");
        trackedLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        trackedLabel.setFont(trackedLabel.getFont().deriveFont(Font.BOLD, 12f));
        trackedLabel.setBorder(new EmptyBorder(6, 0, 4, 0));

        // --- Totals panel ---
        JPanel totalsPanel = new JPanel();
        totalsPanel.setLayout(new BoxLayout(totalsPanel, BoxLayout.Y_AXIS));
        totalsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        totalsPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

        JLabel totalsTitle = new JLabel("TOTALS");
        totalsTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        totalsTitle.setFont(totalsTitle.getFont().deriveFont(Font.BOLD, 10f));
        totalsTitle.setBorder(new EmptyBorder(0, 0, 4, 0));

        totalHighLabel = new JLabel("High:  —");
        totalHighLabel.setForeground(COLOR_HIGH);
        totalHighLabel.setFont(totalHighLabel.getFont().deriveFont(Font.BOLD, 11f));

        totalLowLabel = new JLabel("Low:   —");
        totalLowLabel.setForeground(COLOR_LOW);
        totalLowLabel.setFont(totalLowLabel.getFont().deriveFont(Font.BOLD, 11f));

        totalAvgLabel = new JLabel("Avg:   —");
        totalAvgLabel.setForeground(COLOR_AVG);
        totalAvgLabel.setFont(totalAvgLabel.getFont().deriveFont(Font.BOLD, 11f));

        totalsPanel.add(totalsTitle);
        totalsPanel.add(totalHighLabel);
        totalsPanel.add(totalLowLabel);
        totalsPanel.add(totalAvgLabel);

        // --- Last refresh label ---
        lastRefreshLabel = new JLabel("Prices not yet loaded");
        lastRefreshLabel.setForeground(new Color(150, 150, 150));
        lastRefreshLabel.setFont(lastRefreshLabel.getFont().deriveFont(Font.ITALIC, 10f));
        lastRefreshLabel.setBorder(new EmptyBorder(4, 0, 0, 0));

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bottomPanel.add(totalsPanel);
        bottomPanel.add(lastRefreshLabel);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        topPanel.add(title);
        topPanel.add(searchField);
        topPanel.add(Box.createVerticalStrut(4));
        topPanel.add(searchResultsPanel);
        topPanel.add(trackedLabel);

        add(topPanel, BorderLayout.NORTH);
        add(trackedItemsPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        refreshAgeTimer = new Timer(1000, e -> updateRefreshLabel());
        refreshAgeTimer.start();
    }

    private void updateRefreshLabel()
    {
        if (lastPriceRefresh == null)
        {
            lastRefreshLabel.setText("Prices not yet loaded");
        }
        else
        {
            long secondsAgo = ChronoUnit.SECONDS.between(lastPriceRefresh, Instant.now());
            lastRefreshLabel.setText("Prices updated " + formatAge(secondsAgo) + " ago");
        }
    }

    private void onSearch(String query)
    {
        if (query == null || query.trim().length() < 2)
        {
            searchResultsPanel.setVisible(false);
            return;
        }

        List<ItemPrice> results = itemManager.search(query);
        searchResultsPanel.removeAll();

        int shown = 0;
        for (ItemPrice item : results)
        {
            if (shown >= 5) break;
            JPanel row = buildSearchResultRow(item.getId(), item.getName());
            searchResultsPanel.add(row);
            searchResultsPanel.add(Box.createVerticalStrut(2));
            shown++;
        }

        searchResultsPanel.setVisible(shown > 0);
        searchResultsPanel.revalidate();
        searchResultsPanel.repaint();
    }

    private JPanel buildSearchResultRow(int itemId, String itemName)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(4, 6, 4, 6));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        JLabel nameLabel = new JLabel(itemName);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(11f));

        JButton addBtn = new JButton("+");
        addBtn.setPreferredSize(new Dimension(28, 22));
        addBtn.setBackground(new Color(0, 153, 0));
        addBtn.setForeground(Color.WHITE);
        addBtn.setFocusPainted(false);
        addBtn.setBorderPainted(false);
        addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addBtn.addActionListener(e ->
        {
            onAddItem.accept(itemId);
            searchField.setText("");
            searchResultsPanel.setVisible(false);
        });

        row.add(nameLabel, BorderLayout.CENTER);
        row.add(addBtn, BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e) { row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR); }

            @Override
            public void mouseExited(MouseEvent e) { row.setBackground(ColorScheme.DARKER_GRAY_COLOR); }
        });

        return row;
    }

    public void rebuild(List<TrackedItem> items, Instant newLastPriceRefresh)
    {
        this.lastPriceRefresh = newLastPriceRefresh;
        SwingUtilities.invokeLater(() ->
        {
            trackedItemsPanel.removeAll();

            long totalHigh = 0, totalLow = 0, totalAvg = 0;
            ValueFormat itemFmt = itemValueFormatSupplier.get();
            ValueFormat totalFmt = totalValueFormatSupplier.get();
            PriceDisplay display = priceDisplaySupplier.get();

            if (items.isEmpty())
            {
                JLabel empty = new JLabel("No items tracked. Search above to add one.");
                empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                empty.setFont(empty.getFont().deriveFont(Font.ITALIC, 11f));
                empty.setBorder(new EmptyBorder(8, 0, 0, 0));
                trackedItemsPanel.add(empty);
            }
            else
            {
                for (TrackedItem item : items)
                {
                    totalHigh += item.getHighValue();
                    totalLow  += item.getLowValue();
                    totalAvg  += item.getAvgValue();
                    trackedItemsPanel.add(buildTrackedItemRow(item, itemFmt, display));
                    trackedItemsPanel.add(Box.createVerticalStrut(4));
                }
            }

            boolean hasPrices = items.stream().anyMatch(TrackedItem::hasPrices);
            boolean showHighLow = display == PriceDisplay.HIGH_LOW || display == PriceDisplay.BOTH;
            boolean showAvg     = display == PriceDisplay.AVERAGE  || display == PriceDisplay.BOTH;

            totalHighLabel.setVisible(showHighLow);
            totalLowLabel.setVisible(showHighLow);
            totalAvgLabel.setVisible(showAvg);

            totalHighLabel.setText("High:  " + (hasPrices ? formatGp(totalHigh, totalFmt) : "—"));
            totalLowLabel.setText( "Low:   " + (hasPrices ? formatGp(totalLow,  totalFmt) : "—"));
            String avgTotalLabel = display == PriceDisplay.AVERAGE ? "Value" : "Avg";
            totalAvgLabel.setText(avgTotalLabel + ":   " + (hasPrices ? formatGp(totalAvg, totalFmt) : "—"));

            trackedItemsPanel.revalidate();
            trackedItemsPanel.repaint();
        });
    }

    private JPanel buildTrackedItemRow(TrackedItem item, ValueFormat fmt, PriceDisplay display)
    {
        JPanel card = new JPanel(new BorderLayout(6, 0));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(new EmptyBorder(6, 8, 6, 8));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // Item icon
        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(32, 32));
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        AsyncBufferedImage icon = itemManager.getImage(item.getItemId());
        icon.addTo(iconLabel);
        card.add(iconLabel, BorderLayout.WEST);

        // Center: name + qty + high/low/avg
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel nameLabel = new JLabel(item.getName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 11f));

        JLabel qtyLabel = new JLabel("Qty: " + NUMBER_FORMAT.format(item.getQuantity()));
        qtyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        qtyLabel.setFont(qtyLabel.getFont().deriveFont(11f));

        centerPanel.add(nameLabel);
        centerPanel.add(qtyLabel);

        if (!item.hasPrices())
        {
            JLabel loading = new JLabel("Prices loading...");
            loading.setForeground(new Color(150, 150, 150));
            loading.setFont(loading.getFont().deriveFont(Font.ITALIC, 10f));
            centerPanel.add(loading);
        }
        else
        {
            boolean showHighLow = display == PriceDisplay.HIGH_LOW || display == PriceDisplay.BOTH;
            boolean showAvg     = display == PriceDisplay.AVERAGE  || display == PriceDisplay.BOTH;

            if (showHighLow)
            {
                centerPanel.add(makePriceRow("High", formatGp(item.getHighValue(), fmt), COLOR_HIGH));
                centerPanel.add(makePriceRow("Low",  formatGp(item.getLowValue(),  fmt), COLOR_LOW));
            }
            if (showAvg)
            {
                String avgLabel = display == PriceDisplay.AVERAGE ? "Value" : "Avg";
                centerPanel.add(makePriceRow(avgLabel, formatGp(item.getAvgValue(), fmt), COLOR_AVG));
            }
        }

        card.add(centerPanel, BorderLayout.CENTER);

        // Remove button
        JButton removeBtn = new JButton("✕");
        removeBtn.setPreferredSize(new Dimension(24, 24));
        removeBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        removeBtn.setForeground(new Color(200, 60, 60));
        removeBtn.setFocusPainted(false);
        removeBtn.setBorderPainted(false);
        removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeBtn.setToolTipText("Remove from tracking");
        removeBtn.setVerticalAlignment(SwingConstants.TOP);
        removeBtn.addActionListener(e -> onRemoveItem.accept(item.getItemId()));
        card.add(removeBtn, BorderLayout.EAST);

        return card;
    }

    private JLabel makePriceRow(String label, String value, Color color)
    {
        JLabel lbl = new JLabel(label + ": " + value);
        lbl.setForeground(color);
        lbl.setFont(lbl.getFont().deriveFont(11f));
        return lbl;
    }

    private String formatGp(long value, ValueFormat fmt)
    {
        if (fmt == ValueFormat.FULL)
        {
            return NUMBER_FORMAT.format(value) + " gp";
        }
        if (value >= 1_000_000_000)
        {
            return String.format("%.2fB gp", value / 1_000_000_000.0);
        }
        else if (value >= 1_000_000)
        {
            return String.format("%.2fM gp", value / 1_000_000.0);
        }
        else if (value >= 1_000)
        {
            return String.format("%.1fK gp", value / 1_000.0);
        }
        return NUMBER_FORMAT.format(value) + " gp";
    }

    private String formatAge(long seconds)
    {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h";
    }
}
