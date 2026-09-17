package com.lootingbagviewer;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class LootingBagViewerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(LootingBagViewerPlugin.class);
		RuneLite.main(args);
	}
}
