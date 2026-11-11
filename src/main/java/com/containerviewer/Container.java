package com.containerviewer;

import java.util.Map;
import net.runelite.api.Client;

/**
 * One of the game's storage items, and how what is inside it can be read. Which of the three ways
 * applies is not a choice, it is what the game gives us for that item.
 */
final class Container
{
	enum Source
	{
		/** The server sends the whole thing as an item container, so it is always right. */
		CONTAINER,
		/** Every slot is a varbit, so it is always right and never needs checking. */
		VARBITS,
		/**
		 * Nothing but the listing the game writes in the chat when you check it. What goes in and
		 * out between checks has to be followed from the inventory, and some of it cannot be seen
		 * at all, so these are the only ones that can fall behind.
		 */
		CHAT
	}

	interface VarbitContents
	{
		void read(Client client, Map<Integer, Integer> out);
	}

	/** Config key and storage key, so renaming one of these would forget what it held. */
	final String key;

	/** What the container is called, for the config and for the tooltip's own messages. */
	final String name;

	final Source source;

	/** Every form of the item, closed, open and upgraded, since the tooltip hangs off hovering it. */
	final int[] items;

	/** CONTAINER: the item container the server sends it in. */
	final int inventoryId;

	/** CONTAINER: an interface of its own which already shows the contents, if it has one. */
	final int interfaceId;

	/** VARBITS: how to read the slots. */
	final VarbitContents varbits;

	/**
	 * CHAT: the items it takes, when they are few enough to be worth naming. Used to tell which of
	 * the inventory's comings and goings were the container's doing, and to read the listing without
	 * having to look item names up. Null where the item list is too broad to be useful, and then
	 * only what the last listing held can be followed.
	 */
	final int[] accepted;

	/** How many items fit in all, and how many of each kind, or 0 where that is not a fixed number. */
	final int capacity;
	final int perItem;

	/**
	 * CHAT: the forms of the item that are open, which is when the game puts what you pick up
	 * straight into it rather than into the inventory.
	 */
	final int[] openItems;

	/** The upgraded form of the item, which holds more, and what it holds. */
	final int[] upgradeItems;
	final int upgradeCapacity;
	final int upgradePerItem;

	private Container(Builder builder)
	{
		this.key = builder.key;
		this.name = builder.name;
		this.source = builder.source;
		this.items = builder.items;
		this.inventoryId = builder.inventoryId;
		this.interfaceId = builder.interfaceId;
		this.varbits = builder.varbits;
		this.accepted = builder.accepted;
		this.openItems = builder.openItems;
		this.capacity = builder.capacity;
		this.perItem = builder.perItem;
		this.upgradeItems = builder.upgradeItems;
		this.upgradeCapacity = builder.upgradeCapacity;
		this.upgradePerItem = builder.upgradePerItem;
	}

	boolean holds(int itemId)
	{
		return contains(items, itemId) || contains(upgradeItems, itemId);
	}

	/** Whether the form being carried is the open one, which picks things up by itself. */
	boolean isOpen(int carried)
	{
		return contains(openItems, carried);
	}

	boolean takes(int itemId)
	{
		return accepted != null && contains(accepted, itemId);
	}

	private boolean isUpgrade(int itemId)
	{
		return contains(upgradeItems, itemId);
	}

	/** How many items the form of the container being carried holds, or 0 if that is not fixed. */
	int capacity(int carried)
	{
		return isUpgrade(carried) ? upgradeCapacity : capacity;
	}

	/** How many of one kind the form being carried holds, or 0 if that is not fixed. */
	int perItem(int carried)
	{
		return isUpgrade(carried) ? upgradePerItem : perItem;
	}

	private static boolean contains(int[] ids, int itemId)
	{
		if (ids == null)
		{
			return false;
		}

		for (int id : ids)
		{
			if (id == itemId)
			{
				return true;
			}
		}
		return false;
	}

	static Builder container(String key, String name, int inventoryId, int... items)
	{
		return new Builder(key, name, Source.CONTAINER, items).inventoryId(inventoryId);
	}

	static Builder varbits(String key, String name, VarbitContents varbits, int... items)
	{
		return new Builder(key, name, Source.VARBITS, items).varbits(varbits);
	}

	static Builder chat(String key, String name, int... items)
	{
		return new Builder(key, name, Source.CHAT, items);
	}

	static final class Builder
	{
		private final String key;
		private final String name;
		private final Source source;
		private final int[] items;
		private int inventoryId;
		private int interfaceId;
		private VarbitContents varbits;
		private int[] accepted;
		private int[] openItems;
		private int capacity;
		private int perItem;
		private int[] upgradeItems;
		private int upgradeCapacity;
		private int upgradePerItem;

		private Builder(String key, String name, Source source, int[] items)
		{
			this.key = key;
			this.name = name;
			this.source = source;
			this.items = items;
		}

		private Builder inventoryId(int inventoryId)
		{
			this.inventoryId = inventoryId;
			return this;
		}

		private Builder varbits(VarbitContents varbits)
		{
			this.varbits = varbits;
			return this;
		}

		Builder iface(int interfaceId)
		{
			this.interfaceId = interfaceId;
			return this;
		}

		Builder accepts(int... accepted)
		{
			this.accepted = accepted;
			return this;
		}

		Builder openWhen(int... openItems)
		{
			this.openItems = openItems;
			return this;
		}

		Builder holding(int capacity)
		{
			this.capacity = capacity;
			return this;
		}

		Builder holdingEach(int perItem)
		{
			this.perItem = perItem;
			return this;
		}

		Builder upgrade(int capacity, int perItem, int... items)
		{
			this.upgradeItems = items;
			this.upgradeCapacity = capacity;
			this.upgradePerItem = perItem;
			return this;
		}

		Container build()
		{
			return new Container(this);
		}
	}
}
