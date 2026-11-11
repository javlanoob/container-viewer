package com.containerviewer;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(ContainerViewerConfig.GROUP)
public interface ContainerViewerConfig extends Config
{
	String GROUP = "containerviewer";

	@ConfigSection(
		name = "Containers",
		description = "Which containers show their contents when you hover over them",
		position = 100
	)
	String containersSection = "containers";

	@ConfigItem(
		keyName = "showOnTop",
		name = "Show above other tooltips",
		description = "Show the contents right below the hover text, above tooltips from other plugins",
		position = 1
	)
	default boolean showOnTop()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFill",
		name = "Show how full it is",
		description = "Show how much is in the container out of what it holds, where the game has a fixed number for that",
		position = 2
	)
	default boolean showFill()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPrices",
		name = "Show total value",
		description = "Show the total Grand Exchange value of the contents below the icons",
		position = 3
	)
	default boolean showPrices()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showHighAlch",
		name = "Show high alch value",
		description = "Show the total high alchemy value of the contents below the icons",
		position = 4
	)
	default boolean showHighAlch()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sortByValue",
		name = "Sort by value",
		description = "List the most valuable stacks first instead of in container order",
		position = 5
	)
	default boolean sortByValue()
	{
		return false;
	}

	@ConfigItem(
		keyName = "textColor",
		name = "Text color",
		description = "The color of the text below the icons",
		position = 6
	)
	default Color textColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "lootingBag",
		name = "Looting bag",
		description = "Looting bag",
		section = containersSection,
		position = 101
	)
	default boolean lootingBag()
	{
		return true;
	}

	@ConfigItem(
		keyName = "seedBox",
		name = "Seed box",
		description = "Seed box",
		section = containersSection,
		position = 102
	)
	default boolean seedBox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tackleBox",
		name = "Tackle box",
		description = "Tackle box",
		section = containersSection,
		position = 103
	)
	default boolean tackleBox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "forestryKit",
		name = "Forestry kit",
		description = "Forestry kit",
		section = containersSection,
		position = 104
	)
	default boolean forestryKit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "huntsmansKit",
		name = "Huntsman's kit",
		description = "Huntsman's kit",
		section = containersSection,
		position = 105
	)
	default boolean huntsmansKit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "quiver",
		name = "Dizana's quiver",
		description = "Dizana's quiver",
		section = containersSection,
		position = 106
	)
	default boolean quiver()
	{
		return true;
	}

	@ConfigItem(
		keyName = "knapsack",
		name = "Barbarian knapsack",
		description = "Barbarian knapsack",
		section = containersSection,
		position = 107
	)
	default boolean knapsack()
	{
		return true;
	}

	@ConfigItem(
		keyName = "runePouch",
		name = "Rune pouch",
		description = "Rune pouch, divine included",
		section = containersSection,
		position = 108
	)
	default boolean runePouch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "plankSack",
		name = "Plank sack",
		description = "Plank sack",
		section = containersSection,
		position = 109
	)
	default boolean plankSack()
	{
		return true;
	}

	@ConfigItem(
		keyName = "herbSack",
		name = "Herb sack",
		description = "Herb sack, silklined included",
		section = containersSection,
		position = 110
	)
	default boolean herbSack()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gemBag",
		name = "Gem bag",
		description = "Gem bag",
		section = containersSection,
		position = 111
	)
	default boolean gemBag()
	{
		return true;
	}

	@ConfigItem(
		keyName = "coalBag",
		name = "Coal bag",
		description = "Coal bag",
		section = containersSection,
		position = 112
	)
	default boolean coalBag()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fishBarrel",
		name = "Fish barrel",
		description = "Fish barrel",
		section = containersSection,
		position = 113
	)
	default boolean fishBarrel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "logBasket",
		name = "Log basket",
		description = "Log basket and forestry basket",
		section = containersSection,
		position = 114
	)
	default boolean logBasket()
	{
		return true;
	}

	@ConfigItem(
		keyName = "flamtaerBag",
		name = "Flamtaer bag",
		description = "Flamtaer bag",
		section = containersSection,
		position = 115
	)
	default boolean flamtaerBag()
	{
		return true;
	}

	@ConfigItem(
		keyName = "gemPouch",
		name = "Gem pouch",
		description = "Gem pouch and gem sack",
		section = containersSection,
		position = 116
	)
	default boolean gemPouch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "essencePouch",
		name = "Essence pouch",
		description = "Essence pouches, small to colossal",
		section = containersSection,
		position = 117
	)
	default boolean essencePouch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "meatPouch",
		name = "Meat pouch",
		description = "Hunter guild meat pouch, both sizes",
		section = containersSection,
		position = 118
	)
	default boolean meatPouch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "furPouch",
		name = "Fur pouch",
		description = "Hunter guild fur pouch, all three sizes",
		section = containersSection,
		position = 119
	)
	default boolean furPouch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "seedSack",
		name = "Seed sack",
		description = "Hunter guild seed sack",
		section = containersSection,
		position = 120
	)
	default boolean seedSack()
	{
		return true;
	}

	@ConfigItem(
		keyName = "scrollSack",
		name = "Scroll sack",
		description = "Scroll sack",
		section = containersSection,
		position = 121
	)
	default boolean scrollSack()
	{
		return true;
	}

	@ConfigItem(
		keyName = "foragersPouch",
		name = "Forager's pouch",
		description = "Forager's pouch",
		section = containersSection,
		position = 122
	)
	default boolean foragersPouch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clothesPouch",
		name = "Clothes pouch",
		description = "Forestry clothes pouch",
		section = containersSection,
		position = 123
	)
	default boolean clothesPouch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "capePouch",
		name = "Cape pouch",
		description = "Forestry cape pouch",
		section = containersSection,
		position = 124
	)
	default boolean capePouch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "secondaryPouch",
		name = "Secondary pouch",
		description = "Mastering Mixology secondary pouch",
		section = containersSection,
		position = 125
	)
	default boolean secondaryPouch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "boltPouch",
		name = "Bolt pouch",
		description = "Bolt pouch. Open it once so the bolts can be named",
		section = containersSection,
		position = 126
	)
	default boolean boltPouch()
	{
		return true;
	}
}
