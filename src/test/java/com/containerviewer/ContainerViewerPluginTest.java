package com.containerviewer;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ContainerViewerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ContainerViewerPlugin.class);
		RuneLite.main(args);
	}
}
