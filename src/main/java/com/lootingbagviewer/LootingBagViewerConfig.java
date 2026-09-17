package com.lootingbagviewer;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(LootingBagViewerConfig.GROUP)
public interface LootingBagViewerConfig extends Config
{
	String GROUP = "lootingbagviewer";

	@ConfigItem(
		keyName = "showOnTop",
		name = "Show above other tooltips",
		description = "Show the bag contents right below the hover text, above tooltips from other plugins",
		position = 1
	)
	default boolean showOnTop()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPrices",
		name = "Show total value",
		description = "Show the total Grand Exchange value of the bag below the icons",
		position = 2
	)
	default boolean showPrices()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showHighAlch",
		name = "Show high alch value",
		description = "Show the total high alchemy value of the bag below the icons",
		position = 3
	)
	default boolean showHighAlch()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sortByValue",
		name = "Sort by value",
		description = "List the most valuable stacks first instead of in bag order",
		position = 4
	)
	default boolean sortByValue()
	{
		return false;
	}

	@ConfigItem(
		keyName = "textColor",
		name = "Text color",
		description = "The color of the value text below the icons",
		position = 5
	)
	default Color textColor()
	{
		return Color.WHITE;
	}
}
