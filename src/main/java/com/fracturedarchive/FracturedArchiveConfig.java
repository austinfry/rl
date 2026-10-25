package com.fracturedarchive;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("fractured-archive")
public interface FracturedArchiveConfig extends Config
{
	@ConfigItem(
		keyName = "showSplits",
		name = "Show splits",
		description = "Show the room split times and the Raid total. Turn this off to hide the splits display.",
		position = 0
	)
	default boolean showSplits()
	{
		return true;
	}
}
