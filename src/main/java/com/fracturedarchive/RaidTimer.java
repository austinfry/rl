package com.fracturedarchive;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Elapsed-time state for one raid attempt. Rooms are recorded in the order they
 * are fought. Time spent between bosses is included in the next room's split.
 */
public final class RaidTimer
{
	public static final class Split
	{
		private final ArchiveBoss boss;
		private final String label;
		private final Duration duration;

		Split(ArchiveBoss boss, String label, Duration duration)
		{
			this.boss = boss;
			this.label = label;
			this.duration = duration;
		}

		public ArchiveBoss getBoss()
		{
			return boss;
		}

		public String getLabel()
		{
			return label;
		}

		public Duration getDuration()
		{
			return duration;
		}
	}

	private final List<Split> splits = new ArrayList<Split>();
	private Instant raidStart;
	private Instant segmentStart;
	private Duration frozenTotal;
	private ArchiveBoss currentBoss;
	private String currentLabel;
	private int deathCount;
	private boolean finished;

	public void reset()
	{
		splits.clear();
		raidStart = null;
		segmentStart = null;
		frozenTotal = null;
		currentBoss = null;
		currentLabel = null;
		deathCount = 0;
		finished = false;
	}

	public boolean isIdle()
	{
		return raidStart == null;
	}

	public boolean isRunning()
	{
		return raidStart != null && !finished;
	}

	public boolean isFinished()
	{
		return finished;
	}

	public boolean hasDisplay()
	{
		return raidStart != null;
	}

	public void start(Instant now)
	{
		if (raidStart != null || now == null)
		{
			return;
		}
		raidStart = now;
		segmentStart = now;
		finished = false;
		frozenTotal = null;
		currentBoss = null;
		currentLabel = null;
		deathCount = 0;
		splits.clear();
	}

	/**
	 * Labels the open segment with {@code boss}. A different boss closes the previous segment first.
	 */
	public void enterBoss(ArchiveBoss boss, String label, Instant now)
	{
		if (finished || boss == null || now == null)
		{
			return;
		}
		if (raidStart == null)
		{
			start(now);
		}
		if (currentBoss == boss)
		{
			if (label != null && !label.isEmpty())
			{
				currentLabel = label;
			}
			return;
		}
		if (currentBoss != null)
		{
			completeSegment(now);
		}
		currentBoss = boss;
		currentLabel = label;
		deathCount = 0;
	}

	/**
	 * Counts one death toward {@code boss}. The room is stored once {@code deathsRequired} is met.
	 * Ocellus also freezes the raid total.
	 *
	 * @return true when this death completed the room
	 */
	public boolean recordDeath(ArchiveBoss boss, String label, int deathsRequired, Instant now)
	{
		if (finished || boss == null || now == null)
		{
			return false;
		}
		if (currentBoss != boss && hasSplitFor(boss))
		{
			return false;
		}
		enterBoss(boss, label, now);
		if (currentBoss != boss)
		{
			return false;
		}
		deathCount++;
		int required = deathsRequired < 1 ? 1 : deathsRequired;
		if (deathCount < required)
		{
			return false;
		}
		boolean finalBoss = boss.isFinalBoss();
		completeSegment(now);
		if (finalBoss)
		{
			finish(now);
		}
		return true;
	}

	public void completeCurrent(Instant now)
	{
		if (!isRunning() || now == null || currentBoss == null)
		{
			return;
		}
		boolean finalBoss = currentBoss.isFinalBoss();
		completeSegment(now);
		if (finalBoss)
		{
			finish(now);
		}
	}

	public void finish(Instant now)
	{
		if (raidStart == null || finished || now == null)
		{
			return;
		}
		if (currentBoss != null)
		{
			completeSegment(now);
		}
		finished = true;
		frozenTotal = nonNegative(Duration.between(raidStart, now));
		segmentStart = null;
		currentBoss = null;
		currentLabel = null;
		deathCount = 0;
	}

	public boolean hasSplitFor(ArchiveBoss boss)
	{
		for (int i = 0; i < splits.size(); i++)
		{
			if (splits.get(i).boss == boss)
			{
				return true;
			}
		}
		return false;
	}

	public ArchiveBoss getCurrentBoss()
	{
		return currentBoss;
	}

	public String getCurrentLabel()
	{
		if (!isRunning())
		{
			return null;
		}
		if (currentLabel != null && !currentLabel.isEmpty())
		{
			return currentLabel;
		}
		if (splits.isEmpty())
		{
			return "Current";
		}
		return "Between rooms";
	}

	public List<Split> getSplits()
	{
		return Collections.unmodifiableList(splits);
	}

	public Duration getTotalElapsed(Instant now)
	{
		if (raidStart == null)
		{
			return Duration.ZERO;
		}
		if (finished)
		{
			return frozenTotal == null ? Duration.ZERO : frozenTotal;
		}
		if (now == null)
		{
			return Duration.ZERO;
		}
		return nonNegative(Duration.between(raidStart, now));
	}

	public Duration getCurrentSegmentElapsed(Instant now)
	{
		if (!isRunning() || segmentStart == null || now == null)
		{
			return Duration.ZERO;
		}
		return nonNegative(Duration.between(segmentStart, now));
	}

	public static String format(Duration duration)
	{
		long millis = duration == null ? 0L : duration.toMillis();
		if (millis < 0L)
		{
			millis = 0L;
		}
		long tenths = (millis / 100L) % 10L;
		long totalSeconds = millis / 1000L;
		long seconds = totalSeconds % 60L;
		long minutes = (totalSeconds / 60L) % 60L;
		long hours = totalSeconds / 3600L;
		if (hours > 0L)
		{
			return hours + ":" + pad(minutes) + ":" + pad(seconds) + "." + tenths;
		}
		return minutes + ":" + pad(seconds) + "." + tenths;
	}

	private void completeSegment(Instant now)
	{
		if (segmentStart != null && currentBoss != null && currentLabel != null && !currentLabel.isEmpty())
		{
			splits.add(new Split(currentBoss, currentLabel, nonNegative(Duration.between(segmentStart, now))));
		}
		segmentStart = now;
		currentBoss = null;
		currentLabel = null;
		deathCount = 0;
	}

	private static Duration nonNegative(Duration duration)
	{
		if (duration == null || duration.isNegative())
		{
			return Duration.ZERO;
		}
		return duration;
	}

	private static String pad(long value)
	{
		if (value < 10L)
		{
			return "0" + value;
		}
		return Long.toString(value);
	}
}
