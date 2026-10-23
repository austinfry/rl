package com.fracturedarchive;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FracturedArchivePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(FracturedArchivePlugin.class);
		RuneLite.main(args);
	}
}
