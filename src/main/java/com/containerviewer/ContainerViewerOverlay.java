package com.containerviewer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuEntry;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.QuantityFormatter;

class ContainerViewerOverlay extends Overlay
{
	private static final Color HINT_COLOR = Color.LIGHT_GRAY;
	private static final int PANEL_BORDER = 4;
	private static final int ICON_GAP = 6;
	private static final int MAX_COLUMNS = 4;

	private final Client client;
	private final ContainerViewerPlugin plugin;
	private final ContainerViewerConfig config;
	private final ItemManager itemManager;
	private final TooltipManager tooltipManager;
	private final RuneLiteConfig runeLiteConfig;

	private final PanelComponent panel = new PanelComponent();
	private final PanelComponent grid = new PanelComponent();
	private final Tooltip tooltip = new Tooltip(panel);

	/** What the panel was last built for, so it is built once per hover rather than once per frame. */
	private Container showing;
	private int showingItem;
	private boolean dirty = true;

	@Inject
	ContainerViewerOverlay(Client client, ContainerViewerPlugin plugin, ContainerViewerConfig config,
		ItemManager itemManager, TooltipManager tooltipManager, RuneLiteConfig runeLiteConfig)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.tooltipManager = tooltipManager;
		this.runeLiteConfig = runeLiteConfig;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);

		// Inventory-style grid, 4 icons per row
		grid.setBackgroundColor(null);
		grid.setBorder(new Rectangle());
		grid.setWrap(true);
		grid.setOrientation(ComponentOrientation.HORIZONTAL);
		grid.setGap(new Point(ICON_GAP, ICON_GAP));

		panel.setBorder(new Rectangle(PANEL_BORDER, PANEL_BORDER, PANEL_BORDER, PANEL_BORDER));
	}

	void invalidate()
	{
		dirty = true;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		int hovered = hoveredContainerItem();
		Container container = hovered == -1 ? null : Containers.of(hovered);

		if (container == null)
		{
			showing = null;
			return null;
		}

		// Rebuild when the hover starts so prices and config changes are picked up, without doing
		// the work every frame while the mouse rests on the item
		if (container != showing || hovered != showingItem || dirty)
		{
			rebuild(graphics, container, hovered);
			showing = container;
			showingItem = hovered;
			dirty = false;
		}

		if (config.showOnTop())
		{
			// This overlay renders before Mouse Highlighting, which also adds to the front, so the
			// contents sit right below the hover text instead of under other plugins' tooltips
			tooltipManager.addFront(tooltip);
		}
		else
		{
			tooltipManager.add(tooltip);
		}
		return null;
	}

	/** The container item under the mouse, or -1 if that is not what the mouse is over. */
	private int hoveredContainerItem()
	{
		// Runs every frame, so this stays as cheap as it can be
		if (client.isMenuOpen())
		{
			return -1;
		}

		MenuEntry[] entries = client.getMenu().getMenuEntries();
		if (entries.length == 0)
		{
			return -1;
		}

		// Only item widgets (inventory, bank side inventory, etc.), not one lying on the ground
		Widget widget = entries[entries.length - 1].getWidget();
		if (widget == null)
		{
			return -1;
		}

		int itemId = widget.getItemId();
		Container container = Containers.of(itemId);
		if (container == null || !plugin.enabled(container) || isOwnInterfaceOpen(container))
		{
			return -1;
		}

		return itemId;
	}

	/** Whether the container's own interface is open, since that already shows what is in it. */
	private boolean isOwnInterfaceOpen(Container container)
	{
		if (container.interfaceId == 0)
		{
			return false;
		}

		Widget widget = client.getWidget(container.interfaceId);
		return widget != null && !widget.isHidden();
	}

	private void rebuild(Graphics2D graphics, Container container, int hovered)
	{
		panel.getChildren().clear();

		List<Item> contents = plugin.getContents(container);
		if (contents == null)
		{
			addHint(graphics, container.source == Container.Source.CHAT ? "Check" : "Open");
			return;
		}

		if (contents.isEmpty())
		{
			addHint(graphics, "Empty");
			return;
		}

		List<Stack> stacks = new ArrayList<>(contents.size());
		long total = 0;
		long alchTotal = 0;
		int items = 0;
		for (Item item : contents)
		{
			ItemComposition composition = itemManager.getItemComposition(item.getId());
			long value = (long) itemManager.getItemPrice(item.getId()) * item.getQuantity();
			stacks.add(new Stack(item, value, composition.isStackable()));
			total += value;
			alchTotal += (long) composition.getHaPrice() * item.getQuantity();
			items += item.getQuantity();
		}

		if (config.sortByValue())
		{
			stacks.sort(Comparator.comparingLong((Stack s) -> s.value).reversed());
		}

		// Size the grid to the items, up to 4 per row like the inventory
		int columns = Math.min(stacks.size(), MAX_COLUMNS);
		int gridWidth = columns * Constants.ITEM_SPRITE_WIDTH + (columns - 1) * ICON_GAP;
		grid.setPreferredSize(new Dimension(gridWidth, 0));

		List<String> lines = new ArrayList<>(3);
		int capacity = container.capacity(hovered);
		if (config.showFill() && capacity > 0)
		{
			lines.add(items + " / " + capacity);
		}

		// Label the totals only when both are shown, so a single total stays compact
		boolean showPrices = config.showPrices();
		boolean showHighAlch = config.showHighAlch();
		boolean labelled = showPrices && showHighAlch;
		if (showPrices)
		{
			lines.add((labelled ? "GE: " : "") + QuantityFormatter.quantityToStackSize(total) + " gp");
		}
		if (showHighAlch)
		{
			lines.add("HA: " + QuantityFormatter.quantityToStackSize(alchTotal) + " gp");
		}

		int contentWidth = gridWidth;
		for (String text : lines)
		{
			contentWidth = Math.max(contentWidth, textWidth(graphics, text));
		}
		setPanelWidth(contentWidth);

		grid.getChildren().clear();
		for (Stack stack : stacks)
		{
			Item item = stack.item;
			grid.getChildren().add(new ImageComponent(
				itemManager.getImage(item.getId(), item.getQuantity(), stack.stackable)));
		}
		panel.getChildren().add(grid);

		for (String text : lines)
		{
			panel.getChildren().add(LineComponent.builder()
				.left(text)
				.leftColor(config.textColor())
				.build());
		}
	}

	private void addHint(Graphics2D graphics, String text)
	{
		// Fit short hints snugly; longer ones wrap at the width of a full row of icons
		int fullRowWidth = MAX_COLUMNS * Constants.ITEM_SPRITE_WIDTH + (MAX_COLUMNS - 1) * ICON_GAP;
		setPanelWidth(Math.min(textWidth(graphics, text), fullRowWidth));
		panel.getChildren().add(LineComponent.builder()
			.left(text)
			.leftColor(HINT_COLOR)
			.build());
	}

	private int textWidth(Graphics2D graphics, String text)
	{
		// Tooltips are drawn with the user's tooltip font, not the font this overlay renders with.
		// A little slack so the text never wraps onto a second line
		return graphics.getFontMetrics(runeLiteConfig.tooltipFont().getFont()).stringWidth(text) + 4;
	}

	private void setPanelWidth(int contentWidth)
	{
		panel.setPreferredSize(new Dimension(contentWidth + 2 * PANEL_BORDER, 0));
	}

	private static final class Stack
	{
		final Item item;
		final long value;
		final boolean stackable;

		Stack(Item item, long value, boolean stackable)
		{
			this.item = item;
			this.value = value;
			this.stackable = stackable;
		}
	}
}
