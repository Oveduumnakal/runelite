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
import java.awt.event.MouseListener;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
	private final Supplier<Integer> refreshRateSupplier;

	private final IconTextField searchField;
	private final JPanel searchResultsPanel;

	private final JPanel trackedItemsPanel;

	private final JLabel totalHighLabel;
	private final JLabel totalLowLabel;
	private final JLabel totalAvgLabel;
	private final JPanel totalHighRow;
	private final JPanel totalLowRow;
	private final JPanel totalAvgRow;
	private final JLabel lastRefreshLabel;

	private volatile Instant lastPriceRefresh = null;
	private final java.util.Set<Integer> trackedItemIds = new java.util.HashSet<>();

	private int hoveredItemId = -1;
	private final Timer refreshAgeTimer;

	private static final Color LOADING_COLOR = new Color(150, 150, 150);
	private static final long LOADING_GLOW_PERIOD_MS = 2000;
	private static final float LOADING_GLOW_MIN_ALPHA = 0.2f;
	private final List<JLabel> loadingLabels = new ArrayList<>();
	private final Timer loadingGlowTimer;

	private static final long PULSE_DURATION_MS = 500;
	private final List<PulseEntry> pulseEntries = new ArrayList<>();
	private final Timer pulseTimer;
	private final JLabel totalHighDeltaLabel;
	private final JLabel totalLowDeltaLabel;
	private final JLabel totalAvgDeltaLabel;
	private final JLabel coinsIcon;
	private long lastCoinsIconValue = -1;

	private static final class PulseEntry
	{
		final JLabel label;
		final Color base;
		final long start;

		PulseEntry(JLabel label, Color base, long start)
		{
			this.label = label;
			this.base = base;
			this.start = start;
		}
	}

	public ItemTrackerPanel(
			ItemManager itemManager,
			Consumer<Integer> onAddItem,
			Consumer<Integer> onRemoveItem,
			Supplier<ValueFormat> itemValueFormatSupplier,
			Supplier<ValueFormat> totalValueFormatSupplier,
			Supplier<PriceDisplay> priceDisplaySupplier,
			Supplier<Integer> refreshRateSupplier)
	{
		this.itemManager = itemManager;
		this.onAddItem = onAddItem;
		this.onRemoveItem = onRemoveItem;
		this.itemValueFormatSupplier = itemValueFormatSupplier;
		this.totalValueFormatSupplier = totalValueFormatSupplier;
		this.priceDisplaySupplier = priceDisplaySupplier;
		this.refreshRateSupplier = refreshRateSupplier;

		setLayout(new BorderLayout(0, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Item Tracker");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		title.setBorder(new EmptyBorder(0, 0, 4, 0));

		searchResultsPanel = new JPanel();
		searchResultsPanel.setLayout(new BoxLayout(searchResultsPanel, BoxLayout.Y_AXIS));
		searchResultsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchResultsPanel.setVisible(false);

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

		trackedItemsPanel = new JPanel();
		trackedItemsPanel.setLayout(new BoxLayout(trackedItemsPanel, BoxLayout.Y_AXIS));
		trackedItemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel trackedLabel = new JLabel("Tracked Items", SwingConstants.CENTER);
		trackedLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		trackedLabel.setFont(trackedLabel.getFont().deriveFont(Font.BOLD, 12f));
		trackedLabel.setBorder(new EmptyBorder(6, 0, 4, 0));

		JPanel trackedLabelWrapper = new JPanel(new BorderLayout());
		trackedLabelWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		trackedLabelWrapper.add(trackedLabel, BorderLayout.CENTER);

		JPanel totalsPanel = new JPanel(new BorderLayout(6, 0));
		totalsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		totalsPanel.setBorder(new EmptyBorder(6, 8, 6, 8));

		coinsIcon = new JLabel();
		coinsIcon.setPreferredSize(new Dimension(32, 32));
		coinsIcon.setVerticalAlignment(SwingConstants.CENTER);
		updateCoinsIcon(0);
		totalsPanel.add(coinsIcon, BorderLayout.WEST);

		JLabel totalsTitle = new JLabel("Estimated GE Sell Value", SwingConstants.CENTER);
		totalsTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		totalsTitle.setFont(totalsTitle.getFont().deriveFont(Font.BOLD, 12f));
		totalsTitle.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createCompoundBorder(
				new EmptyBorder(10, 0, 0, 0),
				new MatteBorder(1, 0, 0, 0, new Color(80, 80, 80))
			),
			new EmptyBorder(10, 0, 12, 0)
		));

		JPanel totalsRows = new JPanel();
		totalsRows.setLayout(new BoxLayout(totalsRows, BoxLayout.Y_AXIS));
		totalsRows.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		totalHighLabel = new JLabel("High:  —");
		totalHighLabel.setForeground(COLOR_HIGH);
		totalHighLabel.setFont(totalHighLabel.getFont().deriveFont(Font.BOLD, 11f));

		totalLowLabel = new JLabel("Low:   —");
		totalLowLabel.setForeground(COLOR_LOW);
		totalLowLabel.setFont(totalLowLabel.getFont().deriveFont(Font.BOLD, 11f));

		totalAvgLabel = new JLabel("Avg:   —");
		totalAvgLabel.setForeground(COLOR_AVG);
		totalAvgLabel.setFont(totalAvgLabel.getFont().deriveFont(Font.BOLD, 11f));

		totalHighDeltaLabel = createDeltaLabel();
		totalLowDeltaLabel = createDeltaLabel();
		totalAvgDeltaLabel = createDeltaLabel();

		totalHighRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
		totalHighRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		totalHighRow.add(totalHighLabel);
		totalHighRow.add(totalHighDeltaLabel);

		totalLowRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
		totalLowRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		totalLowRow.add(totalLowLabel);
		totalLowRow.add(totalLowDeltaLabel);

		totalAvgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
		totalAvgRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		totalAvgRow.add(totalAvgLabel);
		totalAvgRow.add(totalAvgDeltaLabel);

		totalsRows.add(totalHighRow);
		totalsRows.add(totalLowRow);
		totalsRows.add(totalAvgRow);
		totalsPanel.add(totalsRows, BorderLayout.CENTER);

		lastRefreshLabel = new JLabel("Prices not yet loaded");
		lastRefreshLabel.setForeground(new Color(150, 150, 150));
		lastRefreshLabel.setFont(lastRefreshLabel.getFont().deriveFont(Font.ITALIC, 10f));
		lastRefreshLabel.setBorder(new EmptyBorder(4, 0, 0, 0));

		JPanel bottomPanel = new JPanel(new BorderLayout(0, 0));
		bottomPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bottomPanel.add(totalsTitle, BorderLayout.NORTH);
		bottomPanel.add(totalsPanel, BorderLayout.CENTER);
		bottomPanel.add(lastRefreshLabel, BorderLayout.SOUTH);

		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
		topPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		topPanel.add(title);
		topPanel.add(searchField);
		topPanel.add(Box.createVerticalStrut(4));
		topPanel.add(searchResultsPanel);
		topPanel.add(trackedLabelWrapper);

		add(topPanel, BorderLayout.NORTH);
		add(trackedItemsPanel, BorderLayout.CENTER);
		add(bottomPanel, BorderLayout.SOUTH);

		refreshAgeTimer = new Timer(1000, e -> updateRefreshLabel());
		refreshAgeTimer.start();

		loadingGlowTimer = new Timer(50, e -> updateLoadingGlow());
		loadingGlowTimer.start();

		pulseTimer = new Timer(25, e -> updatePulses());
		pulseTimer.start();
	}

	public void shutdown()
	{
		refreshAgeTimer.stop();
		loadingGlowTimer.stop();
		pulseTimer.stop();
	}

	private static final Dimension DELTA_LABEL_SIZE = new Dimension(12, 12);

	private void addPriceRow(JPanel pricesPanel, int gridy, JLabel valueLabel, PriceIndicatorMode mode, int delta)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		c.gridy = gridy;
		c.gridx = 0;
		c.insets = new Insets(3, 6, 3, 0);
		pricesPanel.add(valueLabel, c);

		JLabel deltaLabel = createDeltaLabel();
		pulseIfShown(deltaLabel, delta, mode);
		c.gridx = 1;
		pricesPanel.add(deltaLabel, c);

		c.gridx = 2;
		c.weightx = 1;
		c.insets = new Insets(0, 0, 0, 0);
		pricesPanel.add(Box.createHorizontalGlue(), c);
	}

	private void updateCoinsIcon(long value)
	{
		int quantity = (int) Math.max(1, Math.min(value, Integer.MAX_VALUE));
		if (quantity == lastCoinsIconValue)
		{
			return;
		}
		lastCoinsIconValue = quantity;
		itemManager.getImage(net.runelite.api.gameval.ItemID.COINS, quantity, false).addTo(coinsIcon);
	}

	private JLabel createDeltaLabel()
	{
		JLabel label = new JLabel();
		label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
		label.setPreferredSize(DELTA_LABEL_SIZE);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		return label;
	}

	private void pulseIfShown(JLabel label, int delta, PriceIndicatorMode mode)
	{
		if (mode == PriceIndicatorMode.OFF || (mode == PriceIndicatorMode.CHANGE && delta == 0))
		{
			return;
		}
		startPulse(label, delta);
	}

	private void startPulse(JLabel label, int delta)
	{
		label.setText(delta > 0 ? "▲" : delta < 0 ? "▼" : "–");
		Color base = delta > 0 ? COLOR_HIGH : delta < 0 ? COLOR_LOW : LOADING_COLOR;
		label.setForeground(new Color(base.getRed(), base.getGreen(), base.getBlue(), 0));
		pulseEntries.add(new PulseEntry(label, base, System.currentTimeMillis()));
	}

	private void updatePulses()
	{
		if (pulseEntries.isEmpty())
		{
			return;
		}

		long now = System.currentTimeMillis();
		java.util.Iterator<PulseEntry> it = pulseEntries.iterator();
		while (it.hasNext())
		{
			PulseEntry p = it.next();
			long elapsed = now - p.start;
			if (elapsed >= PULSE_DURATION_MS)
			{
				p.label.setText("");
				it.remove();
				continue;
			}

			float alpha = (float) Math.sin(Math.PI * elapsed / PULSE_DURATION_MS);
			p.label.setForeground(new Color(
					p.base.getRed(), p.base.getGreen(), p.base.getBlue(),
					Math.round(alpha * 255)));
		}
	}

	private void updateLoadingGlow()
	{
		if (loadingLabels.isEmpty())
		{
			return;
		}

		double phase = (System.currentTimeMillis() % LOADING_GLOW_PERIOD_MS) / (double) LOADING_GLOW_PERIOD_MS;
		double wave = (Math.sin(phase * 2 * Math.PI) + 1) / 2;
		float alpha = LOADING_GLOW_MIN_ALPHA + (1f - LOADING_GLOW_MIN_ALPHA) * (float) wave;
		Color glow = new Color(
				LOADING_COLOR.getRed(), LOADING_COLOR.getGreen(), LOADING_COLOR.getBlue(),
				Math.round(alpha * 255));

		for (JLabel label : loadingLabels)
		{
			label.setForeground(glow);
		}
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
			long rate = Math.max(30, refreshRateSupplier.get());
			long secondsUntil = Math.max(0, rate - secondsAgo);
			lastRefreshLabel.setText("Price refresh in " + secondsUntil + " seconds");
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
			if (trackedItemIds.contains(item.getId())) continue;
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

			@Override
			public void mouseClicked(MouseEvent e)
			{
				onAddItem.accept(itemId);
				searchField.setText("");
				searchResultsPanel.setVisible(false);
			}
		});
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		return row;
	}

	public void rebuild(List<TrackedItem> items, Instant newLastPriceRefresh, PriceIndicatorMode indicatorMode)
	{
		this.lastPriceRefresh = newLastPriceRefresh;
		trackedItemIds.clear();
		for (TrackedItem item : items) trackedItemIds.add(item.getItemId());
		SwingUtilities.invokeLater(() ->
		{
			loadingLabels.clear();
			pulseEntries.clear();
			totalHighDeltaLabel.setText("");
			totalLowDeltaLabel.setText("");
			totalAvgDeltaLabel.setText("");
			trackedItemsPanel.removeAll();

			long totalHigh = 0, totalLow = 0, totalAvg = 0;
			long prevPriceTotalHigh = 0, prevPriceTotalLow = 0, prevPriceTotalAvg = 0;
			boolean anyDeltas = false;
			ValueFormat itemFmt = itemValueFormatSupplier.get();
			ValueFormat totalFmt = totalValueFormatSupplier.get();
			PriceDisplay display = priceDisplaySupplier.get();

			if (items.isEmpty())
			{
				JLabel empty = new JLabel("No items tracked. Search above to add one.", SwingConstants.CENTER);
				empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				empty.setFont(empty.getFont().deriveFont(Font.ITALIC, 11f));
				empty.setBorder(new EmptyBorder(8, 0, 0, 0));

				JPanel emptyWrapper = new JPanel(new BorderLayout());
				emptyWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
				emptyWrapper.add(empty, BorderLayout.CENTER);
				trackedItemsPanel.add(emptyWrapper);
			}
			else
			{
				for (TrackedItem item : items)
				{
					totalHigh += item.getHighValue();
					totalLow  += item.getLowValue();
					totalAvg  += item.getAvgValue();
					if (item.isHasDeltas())
					{
						anyDeltas = true;
						prevPriceTotalHigh += (long) item.getQuantity() * item.getPrevHighPrice();
						prevPriceTotalLow  += (long) item.getQuantity() * item.getPrevLowPrice();
						prevPriceTotalAvg  += (long) item.getQuantity() * item.getPrevAvgPrice();
					}
					else
					{
						prevPriceTotalHigh += item.getHighValue();
						prevPriceTotalLow  += item.getLowValue();
						prevPriceTotalAvg  += item.getAvgValue();
					}
					trackedItemsPanel.add(buildTrackedItemRow(item, itemFmt, display, indicatorMode));
					trackedItemsPanel.add(Box.createVerticalStrut(4));
				}
			}

			boolean hasPrices = items.stream().anyMatch(TrackedItem::hasPrices);
			boolean showHighLow = display == PriceDisplay.HIGH_LOW || display == PriceDisplay.BOTH;
			boolean showAvg     = display == PriceDisplay.AVERAGE  || display == PriceDisplay.BOTH;

			totalHighRow.setVisible(showHighLow);
			totalLowRow.setVisible(showHighLow);
			totalAvgRow.setVisible(showAvg);

			totalHighLabel.setText("High:  " + (hasPrices ? formatGp(totalHigh, totalFmt) : "—"));
			totalLowLabel.setText( "Low:   " + (hasPrices ? formatGp(totalLow,  totalFmt) : "—"));
			String avgTotalLabel = display == PriceDisplay.AVERAGE ? "Value" : "Avg";
			totalAvgLabel.setText(avgTotalLabel + ":   " + (hasPrices ? formatGp(totalAvg, totalFmt) : "—"));

			updateCoinsIcon(hasPrices ? totalAvg : 0);

			if (indicatorMode != PriceIndicatorMode.OFF && hasPrices && anyDeltas)
			{
				pulseIfShown(totalHighDeltaLabel, Long.compare(totalHigh, prevPriceTotalHigh), indicatorMode);
				pulseIfShown(totalLowDeltaLabel,  Long.compare(totalLow,  prevPriceTotalLow),  indicatorMode);
				pulseIfShown(totalAvgDeltaLabel,  Long.compare(totalAvg,  prevPriceTotalAvg),  indicatorMode);
			}

			trackedItemsPanel.revalidate();
			trackedItemsPanel.repaint();
		});
	}

	private JPanel buildTrackedItemRow(TrackedItem item, ValueFormat fmt, PriceDisplay display, PriceIndicatorMode indicatorMode)
	{
		final PriceIndicatorMode itemIndicatorMode = item.isHasDeltas() ? indicatorMode : PriceIndicatorMode.OFF;
		final boolean hovered = item.getItemId() == hoveredItemId;
		JPanel card = new JPanel(new BorderLayout(6, 0));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(6, 8, 6, 8));
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(32, 32));
		iconLabel.setVerticalAlignment(SwingConstants.CENTER);
		AsyncBufferedImage icon = itemManager.getImage(item.getItemId());
		icon.addTo(iconLabel);
		card.add(iconLabel, BorderLayout.WEST);

		final Color REMOVE_COLOR = new Color(200, 60, 60);
		final Color REMOVE_HIDDEN = new Color(0, 0, 0, 0);
		JButton removeBtn = new JButton("✕");
		removeBtn.setPreferredSize(new Dimension(20, 20));
		removeBtn.setMargin(new Insets(0, 0, 0, 0));
		removeBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		removeBtn.setForeground(hovered ? REMOVE_COLOR : REMOVE_HIDDEN);
		removeBtn.setFont(removeBtn.getFont().deriveFont(removeBtn.getFont().getSize() * 2f / 3f));
		removeBtn.setFocusPainted(false);
		removeBtn.setBorderPainted(false);
		removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		removeBtn.setToolTipText("Remove from tracking");
		removeBtn.addActionListener(e -> onRemoveItem.accept(item.getItemId()));

		JPanel eastPanel = new JPanel(new BorderLayout());
		eastPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		eastPanel.add(removeBtn, BorderLayout.NORTH);
		card.add(eastPanel, BorderLayout.EAST);

		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
		centerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel nameLabel = new JLabel(item.getName());
		nameLabel.setForeground(Color.WHITE);
		nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 11f));

		JLabel qtyLabel = new JLabel("Qty: " + NUMBER_FORMAT.format(item.getQuantity()));
		qtyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		qtyLabel.setFont(qtyLabel.getFont().deriveFont(11f));

		JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
		nameRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		nameRow.add(nameLabel);
		nameRow.add(qtyLabel);
		centerPanel.add(nameRow);

		final JLabel highLabel;
		final JLabel lowLabel;
		final JLabel avgLabel;

		if (!item.hasPrices())
		{
			final JLabel loading;
			if (!item.isTradeable())
			{
				loading = new JLabel("Item not tradeable");
				loading.setForeground(new Color(150, 150, 150));
			}
			else if (item.isPriceLoadFailed())
			{
				loading = new JLabel("Unable to load price");
				loading.setForeground(COLOR_LOW);
			}
			else
			{
				loading = new JLabel("Prices loading...");
				loading.setForeground(LOADING_COLOR);
				loadingLabels.add(loading);
			}
			loading.setFont(loading.getFont().deriveFont(Font.ITALIC, 10f));

			JPanel loadingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
			loadingRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			loadingRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			loadingRow.add(loading);
			centerPanel.add(loadingRow);
			highLabel = null;
			lowLabel = null;
			avgLabel = null;
		}
		else
		{
			boolean showHighLow = display == PriceDisplay.HIGH_LOW || display == PriceDisplay.BOTH;
			boolean showAvg     = display == PriceDisplay.AVERAGE  || display == PriceDisplay.BOTH;

			JPanel pricesPanel = new JPanel(new GridBagLayout());
			pricesPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			pricesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			int gridy = 0;

			if (showHighLow)
			{
				highLabel = new JLabel(hovered
						? "High (ea): " + formatGp(item.getHighPrice(), fmt)
						: "High: " + formatGp(item.getHighValue(), fmt));
				highLabel.setForeground(COLOR_HIGH);
				highLabel.setFont(highLabel.getFont().deriveFont(11f));
				addPriceRow(pricesPanel, gridy++, highLabel, itemIndicatorMode, item.getHighDelta());

				lowLabel = new JLabel(hovered
						? "Low (ea): " + formatGp(item.getLowPrice(), fmt)
						: "Low: " + formatGp(item.getLowValue(), fmt));
				lowLabel.setForeground(COLOR_LOW);
				lowLabel.setFont(lowLabel.getFont().deriveFont(11f));
				addPriceRow(pricesPanel, gridy++, lowLabel, itemIndicatorMode, item.getLowDelta());
			}
			else
			{
				highLabel = null;
				lowLabel = null;
			}

			if (showAvg)
			{
				String avgLabelText = display == PriceDisplay.AVERAGE ? "Value" : "Avg";
				avgLabel = new JLabel(hovered
						? avgLabelText + " (ea): " + formatGp(item.getAvgPrice(), fmt)
						: avgLabelText + ": " + formatGp(item.getAvgValue(), fmt));
				avgLabel.setForeground(COLOR_AVG);
				avgLabel.setFont(avgLabel.getFont().deriveFont(11f));
				addPriceRow(pricesPanel, gridy, avgLabel, itemIndicatorMode, item.getAvgDelta());
			}
			else
			{
				avgLabel = null;
			}

			centerPanel.add(pricesPanel);
		}

		card.add(centerPanel, BorderLayout.CENTER);

		MouseAdapter hoverListener = new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				hoveredItemId = item.getItemId();
				removeBtn.setForeground(REMOVE_COLOR);
				if (highLabel != null) highLabel.setText("High (ea): " + formatGp(item.getHighPrice(), fmt));
				if (lowLabel  != null) lowLabel.setText("Low (ea): "  + formatGp(item.getLowPrice(),  fmt));
				if (avgLabel  != null)
				{
					String lbl = display == PriceDisplay.AVERAGE ? "Value" : "Avg";
					avgLabel.setText(lbl + " (ea): " + formatGp(item.getAvgPrice(), fmt));
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), card);
				if (!card.contains(p))
				{
					if (hoveredItemId == item.getItemId())
					{
						hoveredItemId = -1;
					}
					removeBtn.setForeground(REMOVE_HIDDEN);
					if (highLabel != null) highLabel.setText("High: " + formatGp(item.getHighValue(), fmt));
					if (lowLabel  != null) lowLabel.setText("Low: "  + formatGp(item.getLowValue(),  fmt));
					if (avgLabel  != null)
					{
						String lbl = display == PriceDisplay.AVERAGE ? "Value" : "Avg";
						avgLabel.setText(lbl + ": " + formatGp(item.getAvgValue(), fmt));
					}
				}
			}
		};
		addListenerRecursively(card, hoverListener);

		return card;
	}

	private void addListenerRecursively(Component c, MouseListener listener)
	{
		c.addMouseListener(listener);
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				addListenerRecursively(child, listener);
			}
		}
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
}
