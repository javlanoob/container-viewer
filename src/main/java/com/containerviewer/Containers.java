package com.containerviewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

/**
 * Every container the client can be told the contents of, and where to read each one from.
 */
final class Containers
{
	/** The rune pouch stores a slot as a number into this list rather than as an item. */
	private static final int[] RUNES = {
		ItemID.AIRRUNE, ItemID.WATERRUNE, ItemID.EARTHRUNE, ItemID.FIRERUNE, ItemID.MINDRUNE,
		ItemID.CHAOSRUNE, ItemID.DEATHRUNE, ItemID.BLOODRUNE, ItemID.COSMICRUNE, ItemID.NATURERUNE,
		ItemID.LAWRUNE, ItemID.BODYRUNE, ItemID.SOULRUNE, ItemID.ASTRALRUNE, ItemID.MISTRUNE,
		ItemID.MUDRUNE, ItemID.DUSTRUNE, ItemID.LAVARUNE, ItemID.STEAMRUNE, ItemID.SMOKERUNE,
		ItemID.WRATHRUNE, ItemID.SUNFIRERUNE, ItemID.AETHERRUNE,
	};

	private static final int[] RUNE_SLOTS = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6,
	};

	private static final int[] RUNE_AMOUNTS = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6,
	};

	/** The plank sack keeps a count per kind of plank, so each varbit belongs to one item. */
	private static final int[] PLANK_COUNTS = {
		VarbitID.PLANK_SACK_PLAIN, VarbitID.PLANK_SACK_OAK, VarbitID.PLANK_SACK_TEAK,
		VarbitID.PLANK_SACK_MAHOGANY, VarbitID.PLANK_SACK_CAMPHOR, VarbitID.PLANK_SACK_IRONWOOD,
		VarbitID.PLANK_SACK_ROSEWOOD,
	};

	private static final int[] PLANKS = {
		ItemID.WOODPLANK, ItemID.PLANK_OAK, ItemID.PLANK_TEAK, ItemID.PLANK_MAHOGANY,
		ItemID.PLANK_CAMPHOR, ItemID.PLANK_IRONWOOD, ItemID.PLANK_ROSEWOOD,
	};

	/** The grimy herbs the herb sack takes. Cleaned, noted and quest herbs are all refused. */
	private static final int[] HERBS = {
		ItemID.UNIDENTIFIED_GUAM, ItemID.UNIDENTIFIED_MARENTILL, ItemID.UNIDENTIFIED_TARROMIN,
		ItemID.UNIDENTIFIED_HARRALANDER, ItemID.UNIDENTIFIED_RANARR, ItemID.UNIDENTIFIED_TOADFLAX,
		ItemID.UNIDENTIFIED_IRIT, ItemID.UNIDENTIFIED_AVANTOE, ItemID.UNIDENTIFIED_KWUARM,
		ItemID.UNIDENTIFIED_HUASCA, ItemID.UNIDENTIFIED_SNAPDRAGON, ItemID.UNIDENTIFIED_CADANTINE,
		ItemID.UNIDENTIFIED_LANTADYME, ItemID.UNIDENTIFIED_DWARF_WEED, ItemID.UNIDENTIFIED_TORSTOL,
	};

	private static final int[] GEMS = {
		ItemID.UNCUT_OPAL, ItemID.UNCUT_JADE, ItemID.UNCUT_RED_TOPAZ, ItemID.UNCUT_SAPPHIRE,
		ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND,
	};

	private static final int[] LOGS = {
		ItemID.LOGS, ItemID.OAK_LOGS, ItemID.WILLOW_LOGS, ItemID.TEAK_LOGS, ItemID.ARCTIC_PINE_LOG,
		ItemID.MAPLE_LOGS, ItemID.MAHOGANY_LOGS, ItemID.YEW_LOGS, ItemID.MAGIC_LOGS,
		ItemID.REDWOOD_LOGS, ItemID.ACHEY_TREE_LOGS,
	};

	private static final int[] TEMPLE_MATERIALS = {
		ItemID.LIMESTONEBRICK, ItemID.SOFTCLAY, ItemID.SWAMPPASTE, ItemID.TIMBERBEAM,
	};

	static final List<Container> ALL = Collections.unmodifiableList(roster());

	private static final Map<Integer, Container> BY_ITEM = byItem();

	private static List<Container> roster()
	{
		List<Container> all = new ArrayList<>();

		// Sent as item containers, so these are right whether or not they are ever checked
		all.add(Container.container("lootingBag", "Looting bag", InventoryID.LOOTING_BAG,
				ItemID.LOOTING_BAG, ItemID.LOOTING_BAG_OPEN)
			.iface(InterfaceID.WildernessLootingbag.UNIVERSE)
			.build());

		all.add(Container.container("seedBox", "Seed box", InventoryID.SEED_BOX,
				ItemID.SEED_BOX, ItemID.SEED_BOX_OPEN)
			.build());

		all.add(Container.container("tackleBox", "Tackle box", InventoryID.TACKLE_BOX,
				ItemID.TACKLE_BOX)
			.build());

		all.add(Container.container("forestryKit", "Forestry kit", InventoryID.FORESTRY_KIT,
				ItemID.FORESTRY_KIT)
			.build());

		all.add(Container.container("huntsmansKit", "Huntsman's kit", InventoryID.HUNTSMANS_KIT,
				ItemID.HUNTSMANS_KIT)
			.build());

		all.add(Container.container("quiver", "Dizana's quiver", InventoryID.DIZANAS_QUIVER_AMMO,
				ItemID.DIZANAS_QUIVER_UNCHARGED, ItemID.DIZANAS_QUIVER_UNCHARGED_TROUVER,
				ItemID.DIZANAS_QUIVER_CHARGED, ItemID.DIZANAS_QUIVER_CHARGED_TROUVER,
				ItemID.DIZANAS_QUIVER_INFINITE, ItemID.DIZANAS_QUIVER_INFINITE_TROUVER)
			.build());

		all.add(Container.container("knapsack", "Barbarian knapsack", InventoryID.BARBARIAN_KNAPSACK,
				ItemID.BARBARIAN_KNAPSACK, ItemID.BARBARIAN_KNAPSACK_CLOSED)
			.build());

		// Kept in varbits, which means the client always knows and nothing has to be checked
		all.add(Container.varbits("runePouch", "Rune pouch", Containers::readRunePouch,
				ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER)
			.upgrade(0, 0, ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER)
			.build());

		all.add(Container.varbits("plankSack", "Plank sack", Containers::readPlankSack,
				ItemID.PLANK_SACK)
			.holding(28)
			.build());

		// Only listed in the chat when checked, so these are followed as well as they can be
		all.add(Container.chat("herbSack", "Herb sack",
				ItemID.SLAYER_HERB_SACK, ItemID.SLAYER_HERB_SACK_OPEN)
			.accepts(HERBS)
			.openWhen(ItemID.SLAYER_HERB_SACK_OPEN, ItemID.SLAYER_HERB_SACK_SILK_OPEN)
			.holding(450)
			.holdingEach(30)
			.upgrade(1500, 100, ItemID.SLAYER_HERB_SACK_SILK, ItemID.SLAYER_HERB_SACK_SILK_OPEN)
			.build());

		all.add(Container.chat("gemBag", "Gem bag",
				ItemID.GEM_BAG, ItemID.GEM_BAG_OPEN, ItemID.REWARD_GEM_BAG,
				ItemID.REWARD_GEM_BAG_GUILD, ItemID.STAR_REWARD_GEM_BAG)
			.accepts(GEMS)
			.openWhen(ItemID.GEM_BAG_OPEN)
			.holdingEach(60)
			.build());

		all.add(Container.chat("coalBag", "Coal bag",
				ItemID.COAL_BAG, ItemID.COAL_BAG_OPEN)
			.accepts(ItemID.COAL)
			.openWhen(ItemID.COAL_BAG_OPEN)
			.build());

		all.add(Container.chat("fishBarrel", "Fish barrel",
				ItemID.FISH_BARREL_CLOSED, ItemID.FISH_BARREL_OPEN)
			.openWhen(ItemID.FISH_BARREL_OPEN)
			.holding(28)
			.build());

		all.add(Container.chat("logBasket", "Log basket",
				ItemID.LOG_BASKET_CLOSED, ItemID.LOG_BASKET_OPEN,
				ItemID.FORESTRY_BASKET_CLOSED, ItemID.FORESTRY_BASKET_OPEN)
			.accepts(LOGS)
			.openWhen(ItemID.LOG_BASKET_OPEN, ItemID.FORESTRY_BASKET_OPEN)
			.holding(28)
			.build());

		all.add(Container.chat("flamtaerBag", "Flamtaer bag",
				ItemID.FLAMTAER_BAG)
			.accepts(TEMPLE_MATERIALS)
			.build());

		return all;
	}

	private static Map<Integer, Container> byItem()
	{
		Map<Integer, Container> byItem = new HashMap<>();
		for (Container container : ALL)
		{
			for (int item : container.items)
			{
				byItem.put(item, container);
			}

			if (container.upgradeItems != null)
			{
				for (int item : container.upgradeItems)
				{
					byItem.put(item, container);
				}
			}
		}
		return Collections.unmodifiableMap(byItem);
	}

	/** The container that item is, or null for an item that holds nothing. */
	static Container of(int itemId)
	{
		return BY_ITEM.get(itemId);
	}

	private static void readRunePouch(net.runelite.api.Client client, Map<Integer, Integer> out)
	{
		for (int slot = 0; slot < RUNE_SLOTS.length; slot++)
		{
			int rune = client.getVarbitValue(RUNE_SLOTS[slot]);
			int amount = client.getVarbitValue(RUNE_AMOUNTS[slot]);

			// The slot numbers the runes from one, and an empty slot is a zero
			if (rune > 0 && rune <= RUNES.length && amount > 0)
			{
				out.merge(RUNES[rune - 1], amount, Integer::sum);
			}
		}
	}

	private static void readPlankSack(net.runelite.api.Client client, Map<Integer, Integer> out)
	{
		for (int kind = 0; kind < PLANK_COUNTS.length; kind++)
		{
			int planks = client.getVarbitValue(PLANK_COUNTS[kind]);
			if (planks > 0)
			{
				out.merge(PLANKS[kind], planks, Integer::sum);
			}
		}
	}

	private Containers()
	{
	}
}
