package com.containerviewer;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * The bolt pouch keeps an exact count per slot, but the slot says which bolt it holds as a number of
 * the game's own, which the client is never given the meaning of. Those numbers are learned from the
 * pouch interface the first time it is opened, and then remembered, so the counts can be named after
 * that without opening it again.
 */
final class BoltPouch
{
	private static final int[] SLOTS = {
		VarbitID.XBOWS_POUCH_SLOT1, VarbitID.XBOWS_POUCH_SLOT2,
		VarbitID.XBOWS_POUCH_SLOT3, VarbitID.XBOWS_POUCH_SLOT4,
	};

	private static final int[] COUNTS = {
		VarbitID.XBOWS_POUCH_NUM1, VarbitID.XBOWS_POUCH_NUM2,
		VarbitID.XBOWS_POUCH_NUM3, VarbitID.XBOWS_POUCH_NUM4,
	};

	/** The slot of the open pouch that shows what each stored slot holds. */
	private static final int[] HOLDERS = {
		InterfaceID.XbowsPouch.BOLT_1, InterfaceID.XbowsPouch.BOLT_2,
		InterfaceID.XbowsPouch.BOLT_3, InterfaceID.XbowsPouch.BOLT_4,
	};

	/** Slot number to bolt. The same for every account, so it is kept outside the profile. */
	private final Map<Integer, Integer> bolts = new HashMap<>();

	/**
	 * Reads the pouch, and says whether what it holds is fully known. A slot whose number has not
	 * been learned yet is left out, and the caller is told so rather than shown a short list.
	 */
	boolean read(Client client, Map<Integer, Integer> out)
	{
		boolean known = true;

		for (int slot = 0; slot < SLOTS.length; slot++)
		{
			int count = client.getVarbitValue(COUNTS[slot]);
			if (count <= 0)
			{
				continue;
			}

			Integer bolt = bolts.get(client.getVarbitValue(SLOTS[slot]));
			if (bolt == null)
			{
				known = false;
				continue;
			}

			out.merge(bolt, count, Integer::sum);
		}

		return known;
	}

	/**
	 * Learns from the pouch interface while it is open. Returns true when a slot number was worked
	 * out that was not known before, so the caller can store the new mapping.
	 */
	boolean learn(Client client)
	{
		boolean learned = false;

		for (int slot = 0; slot < SLOTS.length; slot++)
		{
			int code = client.getVarbitValue(SLOTS[slot]);
			if (code <= 0 || bolts.containsKey(code))
			{
				continue;
			}

			Widget holder = client.getWidget(HOLDERS[slot]);
			if (holder == null)
			{
				continue;
			}

			int bolt = holder.getItemId();
			if (bolt > 0)
			{
				bolts.put(code, bolt);
				learned = true;
			}
		}

		return learned;
	}

	/** Whether the pouch interface is on screen, which is when there is anything to learn from. */
	boolean isOpen(Client client)
	{
		Widget pouch = client.getWidget(InterfaceID.XbowsPouch.CONTENT);
		return pouch != null && !pouch.isHidden();
	}

	Map<Integer, Integer> learned()
	{
		return Collections.unmodifiableMap(new LinkedHashMap<>(bolts));
	}

	void load(Map<Integer, Integer> learned)
	{
		bolts.clear();
		if (learned != null)
		{
			bolts.putAll(learned);
		}
	}
}
