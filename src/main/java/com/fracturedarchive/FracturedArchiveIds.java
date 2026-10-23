package com.fracturedarchive;

/**
 * Raid-wide detection constants. Chat lines and region ids are not confirmed yet.
 * Run {@code ./gradlew run}, enter the raid, and copy the debug lines that start
 * with {@code Candidate region} and {@code Candidate chat} into the arrays below.
 * Boss names and NPC ids live on {@link ArchiveBoss}.
 */
public final class FracturedArchiveIds
{
	private FracturedArchiveIds()
	{
	}

	/**
	 * TODO: Map region ids that belong to The Fractured Archive instance.
	 * While this array is empty the timer does not start or stop from region changes.
	 * Ids of {@code 0} are ignored, so a placeholder zero will not match every region.
	 */
	public static final int[] REGION_IDS = {
		// TODO: e.g. 12345
	};

	/**
	 * TODO: Case-insensitive substrings of game chat that mean a raid has started.
	 * The plugin strips color tags before matching. Leave empty to start on the
	 * Archive Guardian spawn instead.
	 */
	public static final String[] RAID_START_MESSAGES = {
		// TODO: e.g. "You enter the Fractured Archive"
	};

	/**
	 * TODO: Substrings that mean the whole raid is over.
	 */
	public static final String[] RAID_COMPLETE_MESSAGES = {
		// TODO
	};

	/**
	 * TODO: Substrings that end whichever boss room is currently active.
	 * Per-boss lines can also be set on {@link ArchiveBoss}.
	 */
	public static final String[] ROOM_COMPLETE_MESSAGES = {
		// TODO
	};

	/**
	 * Names and chat lines containing one of these words are logged at debug
	 * so the constants above can be filled in from an in-game session.
	 */
	public static final String[] WATCH_KEYWORDS = {
		"archive",
		"fractur",
		"guardian",
		"bork",
		"smork",
		"ezekiel",
		"dreadnoth",
		"moia",
		"xarth",
		"dagon",
		"ocellus",
		"quartermaster"
	};

	public static boolean hasRegionIds()
	{
		for (int i = 0; i < REGION_IDS.length; i++)
		{
			if (REGION_IDS[i] > 0)
			{
				return true;
			}
		}
		return false;
	}

	public static boolean containsRegion(int[] regions)
	{
		if (regions == null || !hasRegionIds())
		{
			return false;
		}
		for (int i = 0; i < regions.length; i++)
		{
			for (int j = 0; j < REGION_IDS.length; j++)
			{
				int regionId = REGION_IDS[j];
				if (regionId > 0 && regionId == regions[i])
				{
					return true;
				}
			}
		}
		return false;
	}
}
