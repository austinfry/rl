package com.fracturedarchive;

/**
 * Optional region fallback for The Fractured Archive. Challenge chat starts and
 * ends each split; these region ids are used only when no challenge line has been seen.
 * Boss NPC ids live on {@link ArchiveBoss}.
 */
public final class FracturedArchiveIds
{
	private FracturedArchiveIds()
	{
	}

	/**
	 * Map region ids that belong to The Fractured Archive instance.
	 * While this array is empty, region changes do not start or stop the timer.
	 * Ids of {@code 0} are ignored, so a placeholder zero will not match every region.
	 */
	public static final int[] REGION_IDS = {
		// TODO: e.g. 12345
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
