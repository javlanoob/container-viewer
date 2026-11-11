package com.containerviewer;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.http.api.item.ItemPrice;

@Slf4j
@PluginDescriptor(
	name = "Container Viewer",
	description = "Shows the contents of bags, sacks, pouches and boxes when you hover over them",
	tags = {"looting", "bag", "sack", "pouch", "box", "barrel", "basket", "quiver", "tooltip", "storage"}
)
public class ContainerViewerPlugin extends Plugin
{
	private static final String STORAGE_PREFIX = "contents_";

	/** Where the bolt slot numbers the pouch has given away are kept. */
	private static final String BOLT_SLOTS_KEY = "boltSlots";

	/** Clicks on the container itself that move things rather than list them. */
	private static final String FILL = "Fill";
	private static final String EMPTY = "Empty";

	/** Clicks that have nothing to do with the contents, so they do not arm a listing. */
	private static final String DROP = "Drop";
	private static final String EXAMINE = "Examine";

	/** The listing is written in one go, so it is taken once no more lines are coming. */
	private static final int LISTING_TICKS = 2;

	/** How long to let the inventory catch up with a fill, an empty or a pickup. */
	private static final int TRANSFER_TIMEOUT_TICKS = 3;
	private static final int PICKUP_TIMEOUT_TICKS = 50;

	/** Each line of a listing, as "7 x Grimy guam leaf". */
	private static final String COUNT_SEPARATOR = " x ";

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
	private ContainerViewerOverlay overlay;

	/**
	 * What each container holds, by container key. A container missing from here has never told us
	 * what is in it. Only the ones the game does not keep in varbits are in here, since those are
	 * read straight off the varbits whenever they are shown.
	 */
	private final Map<String, Map<Integer, Integer>> contents = new HashMap<>();

	private Map<Integer, Integer> inventory = new HashMap<>();

	/** The listing being read out of the chat, kept in the order the game lists it. */
	private Container reading;
	private Map<Integer, Integer> readingItems;
	private boolean readingEmpty;
	private int readingTick;

	/** A container that was opened and may turn out to be empty, which the server does not say. */
	private Container awaiting;
	private int awaitingTick;

	/** Items on their way into a container, or out of it, and which way round. */
	private Container transfer;
	private int transferItem;
	private boolean filling;
	private int transferTick;

	/** Something clicked on the ground that an open container may take instead of the inventory. */
	private PendingPickup pendingPickup;

	/** Item names from listings, worked out once each. */
	private final Map<String, Integer> itemsByName = new HashMap<>();

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		loadBoltSlots();
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadContents();
				inventory = countItems(client.getItemContainer(InventoryID.INV));
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		contents.clear();
		inventory = new HashMap<>();
		clearPending();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Tick counts start over on login, so anything still pending would never time out
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			clearPending();
		}

		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			inventory = new HashMap<>();
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
		if (event.getContainerId() == InventoryID.INV)
		{
			onInventoryChanged(countItems(event.getItemContainer()));
			return;
		}

		// A container the server sends in full, which is as good as it gets
		for (Container container : Containers.ALL)
		{
			if (container.source == Container.Source.CONTAINER
				&& container.inventoryId == event.getContainerId())
			{
				if (awaiting == container)
				{
					awaiting = null;
				}

				setContents(container, countItems(event.getItemContainer()));
				return;
			}
		}
	}

	private void onInventoryChanged(Map<Integer, Integer> updated)
	{
		// Items that left the inventory in the moment a container was filled went into it, and items
		// that arrived in the moment it was emptied came out of it
		if (transfer != null)
		{
			for (int item : candidates(transfer))
			{
				int moved = inventory.getOrDefault(item, 0) - updated.getOrDefault(item, 0);
				// Which way the count went is what says this was the container's doing, and the
				// container gains exactly what the inventory lost
				if (filling ? moved > 0 : moved < 0)
				{
					add(transfer, transferItem, item, moved);
				}
			}
		}

		inventory = updated;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (reading == null || event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}

		String message = event.getMessage();

		// The game says so plainly when there is nothing in there to list
		if (message.endsWith("is empty.") || message.endsWith("is empty!"))
		{
			readingEmpty = true;
			return;
		}

		// A listing is sometimes a line per item and sometimes one line of them all
		for (String fragment : message.split("[,;]"))
		{
			readLine(reading, fragment);
		}
	}

	/**
	 * Takes one listed item off a line of a listing. Lines that name no item are left alone, since
	 * a listing comes wrapped in wording that differs from container to container.
	 */
	private void readLine(Container container, String fragment)
	{
		String line = fragment.trim();
		int separator = line.indexOf(COUNT_SEPARATOR);

		if (separator > 0)
		{
			int item = itemNamed(container, line.substring(separator + COUNT_SEPARATOR.length()));
			int quantity = number(line.substring(0, separator));
			if (item != -1 && quantity > 0)
			{
				readingItems.merge(item, quantity, Integer::sum);
			}
			return;
		}

		// A container that holds the one kind of item says how much of it there is in words of its
		// own, so any number on the line is that count
		if (container.accepted != null && container.accepted.length == 1)
		{
			int quantity = number(line);
			if (quantity > 0)
			{
				readingItems.put(container.accepted[0], quantity);
			}
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();

		if (action == MenuAction.CC_OP || action == MenuAction.CC_OP_LOW_PRIORITY
			|| action == MenuAction.WIDGET_TARGET_ON_WIDGET)
		{
			onContainerClicked(event);
			return;
		}

		if (isGroundItem(action) && "Take".equals(event.getMenuOption()))
		{
			onGroundItemTaken(event);
		}
	}

	private void onContainerClicked(MenuOptionClicked event)
	{
		Widget widget = event.getWidget();
		if (widget == null)
		{
			return;
		}

		int itemId = widget.getItemId();
		Container container = Containers.of(itemId);
		if (container == null || !enabled(container) || container.source == Container.Source.VARBITS)
		{
			return;
		}

		String option = event.getMenuOption();
		Widget selected = client.getSelectedWidget();
		boolean usedOn = selected != null && container.takes(selected.getItemId());

		if (usedOn || FILL.equals(option))
		{
			// The server sends its own containers in full, so only the chat-read ones are followed
			if (container.source == Container.Source.CHAT)
			{
				startTransfer(container, itemId, true);
			}
			return;
		}

		if (EMPTY.equals(option))
		{
			if (container.source != Container.Source.CHAT)
			{
				return;
			}

			// Emptying into an open bank always finds room, so the whole lot goes
			if (isBankOpen())
			{
				setContents(container, new LinkedHashMap<>());
			}
			else
			{
				startTransfer(container, itemId, false);
			}
			return;
		}

		if (DROP.equals(option) || EXAMINE.equals(option))
		{
			return;
		}

		if (container.source == Container.Source.CHAT)
		{
			// Whatever this click was, if it ends up listing the container the listing is read. One
			// that lists nothing simply never arrives
			reading = container;
			readingItems = new LinkedHashMap<>();
			readingEmpty = false;
			readingTick = client.getTickCount();
			return;
		}

		// An empty container of the server's own is sent as nothing at all, so a first look inside
		// that brings no contents with it is what says it is empty
		if (!contents.containsKey(container.key))
		{
			awaiting = container;
			awaitingTick = client.getTickCount();
		}
	}

	private void onGroundItemTaken(MenuOptionClicked event)
	{
		int itemId = event.getId();

		for (Container container : Containers.ALL)
		{
			if (container.source != Container.Source.CHAT || !container.takes(itemId)
				|| !enabled(container))
			{
				continue;
			}

			// Only an open container takes what you pick up, and only while you carry it
			int carried = carried(container);
			if (carried == -1 || !container.isOpen(carried))
			{
				continue;
			}

			WorldView worldView = client.getWorldView(event.getMenuEntry().getWorldViewId());
			if (worldView == null)
			{
				return;
			}

			pendingPickup = new PendingPickup(
				container,
				itemId,
				WorldPoint.fromScene(worldView, event.getParam0(), event.getParam1(), worldView.getPlane()),
				client.getTickCount(),
				inventory.getOrDefault(itemId, 0));
			return;
		}
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

		// Only ours if we are the one standing on it, not someone else taking it from under us
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

		// The bolt pouch only says which bolt a slot holds while it is open
		BoltPouch boltPouch = Containers.boltPouch();
		if (boltPouch.isOpen(client) && boltPouch.learn(client))
		{
			saveBoltSlots();
			overlay.invalidate();
		}

		if (reading != null && tick - readingTick >= LISTING_TICKS)
		{
			// A click that listed nothing leaves what we had rather than claiming it is empty
			if (!readingItems.isEmpty() || readingEmpty)
			{
				setContents(reading, readingItems);
			}

			reading = null;
			readingItems = null;
		}

		if (awaiting != null && tick - awaitingTick >= LISTING_TICKS)
		{
			if (!contents.containsKey(awaiting.key))
			{
				setContents(awaiting, new LinkedHashMap<>());
			}

			awaiting = null;
		}

		if (transfer != null && tick - transferTick > TRANSFER_TIMEOUT_TICKS)
		{
			transfer = null;
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

		// Give the inventory a tick to catch up. If the item is not in there, the container took it
		if (tick > pickup.despawnTick)
		{
			int gained = inventory.getOrDefault(pickup.itemId, 0) - pickup.inventoryBefore;
			int carried = carried(pickup.container);
			if (gained <= 0 && carried != -1 && pickup.container.isOpen(carried))
			{
				add(pickup.container, carried, pickup.itemId, pickup.quantity);
			}

			pendingPickup = null;
		}
	}

	/**
	 * What the container holds, or null if it has never said. Read straight off the varbits for the
	 * containers the game keeps that way, since those cannot be out of date.
	 */
	List<Item> getContents(Container container)
	{
		Map<Integer, Integer> items;

		if (container.source == Container.Source.VARBITS)
		{
			items = new LinkedHashMap<>();
			if (!container.varbits.read(client, items))
			{
				// Part of it cannot be named yet, so say nothing rather than show a short list
				return null;
			}
		}
		else
		{
			items = contents.get(container.key);
			if (items == null)
			{
				return null;
			}
		}

		List<Item> stacks = new ArrayList<>(items.size());
		for (Map.Entry<Integer, Integer> entry : items.entrySet())
		{
			stacks.add(new Item(entry.getKey(), entry.getValue()));
		}
		return stacks;
	}

	boolean enabled(Container container)
	{
		Boolean enabled = configManager.getConfiguration(ContainerViewerConfig.GROUP, container.key, Boolean.class);
		return enabled == null || enabled;
	}

	private void startTransfer(Container container, int itemId, boolean filling)
	{
		this.transfer = container;
		this.transferItem = itemId;
		this.filling = filling;
		this.transferTick = client.getTickCount();
	}

	/**
	 * The items a change in the inventory could have been about. A container with a short enough
	 * list of what it takes is followed by that; for the rest, only what it was last seen holding
	 * can be told apart from the rest of the inventory.
	 */
	private Iterable<Integer> candidates(Container container)
	{
		if (container.accepted != null)
		{
			List<Integer> accepted = new ArrayList<>(container.accepted.length);
			for (int item : container.accepted)
			{
				accepted.add(item);
			}
			return accepted;
		}

		Map<Integer, Integer> held = contents.get(container.key);
		return held == null ? Collections.emptyList() : new ArrayList<>(held.keySet());
	}

	/**
	 * Moves an item in or out of what a container is holding, within what the container holds, so a
	 * fill that was partly refused for want of room is not counted past the top.
	 */
	private void add(Container container, int carried, int item, int quantity)
	{
		Map<Integer, Integer> held = contents.get(container.key);

		// Without a listing to go on, a count of our own would be a guess
		if (held == null || quantity == 0)
		{
			return;
		}

		Map<Integer, Integer> updated = new LinkedHashMap<>(held);
		int total = Math.max(0, updated.getOrDefault(item, 0) + quantity);

		int perItem = container.perItem(carried);
		if (perItem > 0)
		{
			total = Math.min(perItem, total);
		}

		if (total == 0)
		{
			updated.remove(item);
		}
		else
		{
			updated.put(item, total);
		}

		setContents(container, updated);
	}

	private void setContents(Container container, Map<Integer, Integer> items)
	{
		if (items.equals(contents.get(container.key)))
		{
			return;
		}

		log.debug("{} now holds {}", container.name, items);
		contents.put(container.key, Collections.unmodifiableMap(items));
		overlay.invalidate();
		saveContents(container);
	}

	/** The form of the container being carried, or -1 if it is not on us. */
	private int carried(Container container)
	{
		for (int item : inventory.keySet())
		{
			if (container.holds(item))
			{
				return item;
			}
		}
		return -1;
	}

	private boolean isBankOpen()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank != null && !bank.isHidden();
	}

	private void clearPending()
	{
		reading = null;
		readingItems = null;
		awaiting = null;
		transfer = null;
		pendingPickup = null;
	}

	/**
	 * The item a listed name is. The container's own list is looked at first, both because it is
	 * quick and because it keeps a name the game words oddly out of trouble.
	 */
	private int itemNamed(Container container, String name)
	{
		String wanted = name.trim();
		while (wanted.endsWith(".") || wanted.endsWith("!"))
		{
			wanted = wanted.substring(0, wanted.length() - 1).trim();
		}

		if (wanted.isEmpty())
		{
			return -1;
		}

		if (container.accepted != null)
		{
			for (int item : container.accepted)
			{
				if (wanted.equalsIgnoreCase(itemManager.getItemComposition(item).getName()))
				{
					return item;
				}
			}
			return -1;
		}

		Integer known = itemsByName.get(wanted.toLowerCase());
		if (known != null)
		{
			return known;
		}

		int found = -1;
		for (ItemPrice match : itemManager.search(wanted))
		{
			if (wanted.equalsIgnoreCase(match.getName()))
			{
				found = match.getId();
				break;
			}
		}

		itemsByName.put(wanted.toLowerCase(), found);
		return found;
	}

	/** The first number on a line, with the thousands commas the game writes taken out. */
	private static int number(String text)
	{
		StringBuilder digits = new StringBuilder();
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (c >= '0' && c <= '9')
			{
				digits.append(c);
			}
			else if (digits.length() > 0 && c != ',')
			{
				break;
			}
		}

		if (digits.length() == 0 || digits.length() > 9)
		{
			return 0;
		}
		return Integer.parseInt(digits.toString());
	}

	private static boolean isGroundItem(MenuAction action)
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
		Map<Integer, Integer> counts = new LinkedHashMap<>();
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

	/**
	 * The bolt slot numbers are the game's, not an account's, so they are kept outside the profile
	 * and stay learned for every character.
	 */
	private void loadBoltSlots()
	{
		Containers.boltPouch().load(parse(
			configManager.getConfiguration(ContainerViewerConfig.GROUP, BOLT_SLOTS_KEY)));
	}

	private void saveBoltSlots()
	{
		configManager.setConfiguration(ContainerViewerConfig.GROUP, BOLT_SLOTS_KEY,
			serialize(Containers.boltPouch().learned()));
	}

	private void loadContents()
	{
		contents.clear();
		for (Container container : Containers.ALL)
		{
			if (container.source == Container.Source.VARBITS)
			{
				continue;
			}

			Map<Integer, Integer> stored = parse(
				configManager.getRSProfileConfiguration(ContainerViewerConfig.GROUP, STORAGE_PREFIX + container.key));
			if (stored != null)
			{
				contents.put(container.key, stored);
			}
		}
		overlay.invalidate();
	}

	private void saveContents(Container container)
	{
		Map<Integer, Integer> held = contents.get(container.key);
		if (held == null)
		{
			return;
		}

		configManager.setRSProfileConfiguration(ContainerViewerConfig.GROUP,
			STORAGE_PREFIX + container.key, serialize(held));
	}

	private static String serialize(Map<Integer, Integer> items)
	{
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Integer, Integer> entry : items.entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(entry.getKey()).append(':').append(entry.getValue());
		}
		return sb.toString();
	}

	private static Map<Integer, Integer> parse(String value)
	{
		if (value == null)
		{
			return null;
		}

		Map<Integer, Integer> items = new LinkedHashMap<>();
		for (String entry : value.split(","))
		{
			String[] parts = entry.split(":");
			if (parts.length != 2)
			{
				continue;
			}

			try
			{
				int item = Integer.parseInt(parts[0]);
				int quantity = Integer.parseInt(parts[1]);
				if (item > 0 && quantity > 0)
				{
					items.merge(item, quantity, Integer::sum);
				}
			}
			catch (NumberFormatException e)
			{
				log.debug("Skipping malformed stored entry {}", entry);
			}
		}
		return Collections.unmodifiableMap(items);
	}

	private static final class PendingPickup
	{
		final Container container;
		final int itemId;
		final WorldPoint location;
		final int clickTick;
		final int inventoryBefore;
		int quantity;
		int despawnTick = -1;

		PendingPickup(Container container, int itemId, WorldPoint location, int clickTick, int inventoryBefore)
		{
			this.container = container;
			this.itemId = itemId;
			this.location = location;
			this.clickTick = clickTick;
			this.inventoryBefore = inventoryBefore;
		}
	}

	@Provides
	ContainerViewerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ContainerViewerConfig.class);
	}
}
