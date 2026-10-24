package com.fracturedarchive;

/**
 * Optional region ids for The Fractured Archive. Entering a listed region does not
 * start the timer. Leaving one freezes a timer that challenge chat already started.
 * Boss NPC ids live on {@link ArchiveBoss}.
 */
public final class FracturedArchiveIds
{
	private FracturedArchiveIds()
	{
	}

	/**
	 * Map region ids that belong to The Fractured Archive instance.
	 * While this array is empty, region changes do not stop the timer. Entering a
	 * listed region never starts it.
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
