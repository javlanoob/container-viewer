package com.lootingbagviewer;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Looting Bag Viewer",
	description = "Shows the contents of your looting bag when you hover over it",
	tags = {"looting", "bag", "wilderness", "pvp", "tooltip", "loot"}
)
public class LootingBagViewerPlugin extends Plugin
{
	private static final String CONTENTS_KEY = "contents";
	private static final int BAG_SIZE = 28;

	// How long to wait for a deposit to land before giving up on it
	private static final int USE_TIMEOUT_TICKS = 100;
	private static final int PICKUP_TIMEOUT_TICKS = 50;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private LootingBagViewerOverlay overlay;

	/**
	 * Last known bag contents, or null if the bag has never been checked on this account.
	 * The server only sends the looting bag inventory when the bag is checked, so deposits
	 * are tracked locally and contents are persisted per account to survive relogs.
	 */
	@Getter(AccessLevel.PACKAGE)
	private List<Item> contents;

	private Map<Integer, Integer> lastInventory = new HashMap<>();

	@Getter(AccessLevel.PACKAGE)
	private boolean bagInInventory;

	// Tick the bag's own window was opened, so its contents can be read once they load
	private int bagViewedTick = -1;

	// Item used on the bag, waiting for it to leave the inventory (the "How many?" dialog can sit open)
	private int pendingUseItemId = -1;
	private int pendingUseTick;

	// Item clicked on the ground while the bag is open, waiting to see where it ends up
	private PendingPickup pendingPickup;

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadContents();
				updateInventory(countItems(client.getItemContainer(InventoryID.INV)));
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		contents = null;
		updateInventory(new HashMap<>());
		clearPending();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Tick counts restart on login, so anything still pending would never time out
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			clearPending();
		}

		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			updateInventory(new HashMap<>());
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadContents();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.LOOTING_BAG)
		{
			syncFromContainer(event.getItemContainer());
		}
		else if (event.getContainerId() == InventoryID.INV)
		{
			Map<Integer, Integer> inventory = countItems(event.getItemContainer());

			if (pendingUseItemId != -1)
			{
				int removed = lastInventory.getOrDefault(pendingUseItemId, 0) - inventory.getOrDefault(pendingUseItemId, 0);
				if (removed > 0)
				{
					log.debug("Deposited {} x {} by using it on the bag", removed, pendingUseItemId);
					addToBag(pendingUseItemId, removed);
					pendingUseItemId = -1;
				}
			}

			updateInventory(inventory);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.WILDERNESS_LOOTINGBAG)
		{
			bagViewedTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.WILDERNESS_LOOTINGBAG)
		{
			bagViewedTick = -1;
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();

		// Picking an amount in the "How many?" dialog continues the deposit
		if (action == MenuAction.WIDGET_CONTINUE)
		{
			return;
		}

		// Any other click means the player moved on from a deposit that never happened
		pendingUseItemId = -1;

		if (action == MenuAction.WIDGET_TARGET_ON_WIDGET)
		{
			Widget selected = client.getSelectedWidget();
			Widget target = event.getWidget();
			if (selected == null || target == null)
			{
				return;
			}

			// Works both ways round: item on bag, or bag on item. Spells cast on the bag have no item id
			int selectedId = selected.getItemId();
			int targetId = target.getItemId();
			int depositId = isLootingBag(targetId) ? selectedId : isLootingBag(selectedId) ? targetId : -1;
			if (depositId > 0 && !isLootingBag(depositId))
			{
				pendingUseItemId = depositId;
				pendingUseTick = client.getTickCount();
			}
			return;
		}

		if (!isGroundItemAction(action) || !"Take".equals(event.getMenuOption()) || !isBagOpen())
		{
			return;
		}

		WorldView worldView = client.getWorldView(event.getMenuEntry().getWorldViewId());
		if (worldView == null)
		{
			return;
		}

		int itemId = event.getId();
		pendingPickup = new PendingPickup(
			itemId,
			WorldPoint.fromScene(worldView, event.getParam0(), event.getParam1(), worldView.getPlane()),
			client.getTickCount(),
			lastInventory.getOrDefault(itemId, 0));
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		PendingPickup pickup = pendingPickup;
		if (pickup == null || pickup.despawnTick != -1 || event.getItem().getId() != pickup.itemId)
		{
			return;
		}

		WorldPoint location = event.getTile().getWorldLocation();
		if (!location.equals(pickup.location))
		{
			return;
		}

		// Only count it if we're the one standing on the item, not someone else taking it
		Player player = client.getLocalPlayer();
		if (player == null || !player.getWorldLocation().equals(location))
		{
			return;
		}

		pickup.quantity = event.getItem().getQuantity();
		pickup.despawnTick = client.getTickCount();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int tick = client.getTickCount();

		// An empty bag sends no container update when checked, so read it once the window has loaded
		if (bagViewedTick != -1 && tick > bagViewedTick)
		{
			bagViewedTick = -1;
			syncFromContainer(client.getItemContainer(InventoryID.LOOTING_BAG));
		}

		if (pendingUseItemId != -1 && tick - pendingUseTick > USE_TIMEOUT_TICKS)
		{
			pendingUseItemId = -1;
		}

		PendingPickup pickup = pendingPickup;
		if (pickup == null)
		{
			return;
		}

		if (pickup.despawnTick == -1)
		{
			if (tick - pickup.clickTick > PICKUP_TIMEOUT_TICKS)
			{
				pendingPickup = null;
			}
			return;
		}

		// Give the inventory a tick to update. If the item didn't show up there, it went in the bag
		// (with the "supplies go to inventory" setting, food and potions land in the inventory instead)
		if (tick > pickup.despawnTick)
		{
			int gained = lastInventory.getOrDefault(pickup.itemId, 0) - pickup.inventoryCountBefore;
			if (gained <= 0 && isBagOpen())
			{
				log.debug("Picked up {} x {} into the bag", pickup.quantity, pickup.itemId);
				addToBag(pickup.itemId, pickup.quantity);
			}
			pendingPickup = null;
		}
	}

	/**
	 * Replaces the tracked contents with the real ones from the server. A null container means an empty bag.
	 */
	private void syncFromContainer(ItemContainer container)
	{
		List<Item> items = new ArrayList<>();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() > 0 && item.getQuantity() > 0)
				{
					items.add(item);
				}
			}
		}
		setContents(items);
	}

	private void addToBag(int itemId, int quantity)
	{
		// Without a baseline from checking the bag, a partial list would be misleading
		if (contents == null || itemId <= 0 || quantity <= 0)
		{
			return;
		}

		List<Item> items = new ArrayList<>(contents);
		if (itemManager.getItemComposition(itemId).isStackable())
		{
			boolean merged = false;
			for (int i = 0; i < items.size(); i++)
			{
				Item item = items.get(i);
				if (item.getId() == itemId)
				{
					int total = (int) Math.min((long) item.getQuantity() + quantity, Integer.MAX_VALUE);
					items.set(i, new Item(itemId, total));
					merged = true;
					break;
				}
			}

			if (!merged && items.size() < BAG_SIZE)
			{
				items.add(new Item(itemId, quantity));
			}
		}
		else
		{
			for (int i = 0; i < quantity && items.size() < BAG_SIZE; i++)
			{
				items.add(new Item(itemId, 1));
			}
		}

		setContents(items);
	}

	private void setContents(List<Item> items)
	{
		// Checking the bag can report the same contents twice (container update, then the window sync)
		if (items.equals(contents))
		{
			return;
		}

		contents = Collections.unmodifiableList(items);
		overlay.invalidate();
		saveContents();
	}

	private void updateInventory(Map<Integer, Integer> inventory)
	{
		lastInventory = inventory;
		bagInInventory = inventory.containsKey(ItemID.LOOTING_BAG) || isBagOpen();
	}

	private boolean isBagOpen()
	{
		return lastInventory.containsKey(ItemID.LOOTING_BAG_OPEN);
	}

	private void clearPending()
	{
		pendingUseItemId = -1;
		pendingPickup = null;
		bagViewedTick = -1;
	}

	private static boolean isLootingBag(int itemId)
	{
		return itemId == ItemID.LOOTING_BAG || itemId == ItemID.LOOTING_BAG_OPEN;
	}

	private static boolean isGroundItemAction(MenuAction action)
	{
		switch (action)
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
				return true;
			default:
				return false;
		}
	}

	private static Map<Integer, Integer> countItems(ItemContainer container)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (item.getId() > 0 && item.getQuantity() > 0)
				{
					counts.merge(item.getId(), item.getQuantity(), Integer::sum);
				}
			}
		}
		return counts;
	}

	private void loadContents()
	{
		contents = parse(configManager.getRSProfileConfiguration(LootingBagViewerConfig.GROUP, CONTENTS_KEY));
		overlay.invalidate();
	}

	private void saveContents()
	{
		StringBuilder sb = new StringBuilder();
		for (Item item : contents)
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(item.getId()).append(':').append(item.getQuantity());
		}
		configManager.setRSProfileConfiguration(LootingBagViewerConfig.GROUP, CONTENTS_KEY, sb.toString());
	}

	private static List<Item> parse(String value)
	{
		if (value == null)
		{
			return null;
		}

		List<Item> items = new ArrayList<>();
		for (String entry : value.split(","))
		{
			String[] parts = entry.split(":");
			if (parts.length != 2)
			{
				continue;
			}

			try
			{
				items.add(new Item(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
			}
			catch (NumberFormatException e)
			{
				log.debug("Skipping malformed looting bag entry {}", entry);
			}
		}
		return Collections.unmodifiableList(items);
	}

	private static final class PendingPickup
	{
		final int itemId;
		final WorldPoint location;
		final int clickTick;
		final int inventoryCountBefore;
		int quantity;
		int despawnTick = -1;

		PendingPickup(int itemId, WorldPoint location, int clickTick, int inventoryCountBefore)
		{
			this.itemId = itemId;
			this.location = location;
			this.clickTick = clickTick;
			this.inventoryCountBefore = inventoryCountBefore;
		}
	}

	@Provides
	LootingBagViewerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LootingBagViewerConfig.class);
	}
}
