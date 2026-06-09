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

    private final ItemManager itemManager;
    private final Consumer<Integer> onAddItem;
    private final Consumer<Integer> onRemoveItem;
    private final Supplier<ValueFormat> itemValueFormatSupplier;
    private final Supplier<ValueFormat> totalValueFormatSupplier;

    // Search area
    private final IconTextField searchField;
    private final JPanel searchResultsPanel;

    // Tracked items list
    private final JPanel trackedItemsPanel;

    // Total value
    private final JLabel totalValueLabel;
    private final JLabel lastRefreshLabel;

    public ItemTrackerPanel(
            ItemManager itemManager,
            Consumer<Integer> onAddItem,
            Consumer<Integer> onRemoveItem,
            Supplier<ValueFormat> itemValueFormatSupplier,
            Supplier<ValueFormat> totalValueFormatSupplier)
    {
        this.itemManager = itemManager;
        this.onAddItem = onAddItem;
        this.onRemoveItem = onRemoveItem;
        this.itemValueFormatSupplier = itemValueFormatSupplier;
        this.totalValueFormatSupplier = totalValueFormatSupplier;

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

        // --- Total value label ---
        totalValueLabel = new JLabel("Total Value: 0 gp");
        totalValueLabel.setForeground(new Color(255, 200, 0));
        totalValueLabel.setFont(totalValueLabel.getFont().deriveFont(Font.BOLD, 12f));
        totalValueLabel.setBorder(new EmptyBorder(6, 0, 0, 0));

        // --- Last refresh label ---
        lastRefreshLabel = new JLabel("Prices not yet loaded");
        lastRefreshLabel.setForeground(new Color(150, 150, 150));
        lastRefreshLabel.setFont(lastRefreshLabel.getFont().deriveFont(Font.ITALIC, 10f));
        lastRefreshLabel.setBorder(new EmptyBorder(2, 0, 4, 0));

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bottomPanel.add(totalValueLabel);
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
            public void mouseEntered(MouseEvent e)
            {
                row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }
        });

        return row;
    }

    /**
     * Rebuilds the tracked items list from the current tracked item data.
     */
    public void rebuild(List<TrackedItem> items, Instant lastPriceRefresh)
    {
        SwingUtilities.invokeLater(() ->
        {
            trackedItemsPanel.removeAll();

            long total = 0;
            ValueFormat itemFmt = itemValueFormatSupplier.get();

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
                    total += item.getTotalValue();
                    trackedItemsPanel.add(buildTrackedItemRow(item, itemFmt));
                    trackedItemsPanel.add(Box.createVerticalStrut(4));
                }
            }

            ValueFormat totalFmt = totalValueFormatSupplier.get();
            totalValueLabel.setText("Total Value: " + formatGp(total, totalFmt));

            // Update last refresh text
            if (lastPriceRefresh == null)
            {
                lastRefreshLabel.setText("Prices not yet loaded");
            }
            else
            {
                long secondsAgo = ChronoUnit.SECONDS.between(lastPriceRefresh, Instant.now());
                lastRefreshLabel.setText("Prices updated " + formatAge(secondsAgo) + " ago");
            }

            trackedItemsPanel.revalidate();
            trackedItemsPanel.repaint();
        });
    }

    private JPanel buildTrackedItemRow(TrackedItem item, ValueFormat fmt)
    {
        JPanel card = new JPanel(new BorderLayout(6, 0));
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(new EmptyBorder(6, 8, 6, 8));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        // Item icon
        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(32, 32));
        AsyncBufferedImage icon = itemManager.getImage(item.getItemId());
        icon.addTo(iconLabel);
        card.add(iconLabel, BorderLayout.WEST);

        // Center: name + quantity + value
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel nameLabel = new JLabel(item.getName());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 11f));

        String qtyText = "Qty: " + NUMBER_FORMAT.format(item.getQuantity());
        JLabel qtyLabel = new JLabel(qtyText);
        qtyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        qtyLabel.setFont(qtyLabel.getFont().deriveFont(11f));

        String valueText = item.getGePrice() > 0
                ? "Value: " + formatGp(item.getTotalValue(), fmt)
                : "Price: loading...";
        JLabel valueLabel = new JLabel(valueText);
        valueLabel.setForeground(new Color(255, 200, 0));
        valueLabel.setFont(valueLabel.getFont().deriveFont(11f));

        centerPanel.add(nameLabel);
        centerPanel.add(qtyLabel);
        centerPanel.add(valueLabel);
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
        removeBtn.addActionListener(e -> onRemoveItem.accept(item.getItemId()));
        card.add(removeBtn, BorderLayout.EAST);

        return card;
    }

    private String formatGp(long value, ValueFormat fmt)
    {
        if (fmt == ValueFormat.FULL)
        {
            return NUMBER_FORMAT.format(value) + " gp";
        }

        // Abbreviated
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
        if (seconds < 60)
        {
            return seconds + "s";
        }
        else if (seconds < 3600)
        {
            return (seconds / 60) + "m";
        }
        else
        {
            return (seconds / 3600) + "h";
        }
    }
}