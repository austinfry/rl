package com.fracturedarchive;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Elapsed-time state for one raid attempt. Challenge chat records each boss from
 * its started message to its complete message. The NPC fallback includes time
 * spent walking to the next boss in that next split.
 */
public final class RaidTimer
{
	public static final class Split
	{
		private final ArchiveBoss boss;
		private final String label;
		private final Duration duration;
		private String gameDuration;
		private String personalBest;

		Split(ArchiveBoss boss, String label, Duration duration, String gameDuration, String personalBest)
		{
			this.boss = boss;
			this.label = label;
			this.duration = duration;
			this.gameDuration = gameDuration;
			this.personalBest = personalBest;
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

		public String getGameDuration()
		{
			return gameDuration;
		}

		public String getPersonalBest()
		{
			return personalBest;
		}

		void applyGameReport(String gameDuration, String personalBest)
		{
			if (this.gameDuration == null && gameDuration != null)
			{
				this.gameDuration = gameDuration;
			}
			if (this.personalBest == null && personalBest != null)
			{
				this.personalBest = personalBest;
			}
		}
	}

	private final List<Split> splits = new ArrayList<Split>();
	private Instant raidStart;
	private Instant segmentStart;
	private Duration frozenTotal;
	private ArchiveBoss currentBoss;
	private String currentLabel;
	private String gameTotal;
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
		gameTotal = null;
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
		gameTotal = null;
		deathCount = 0;
		splits.clear();
	}

	/**
	 * Opens a chat split. The measured room time starts at {@code now}, so time
	 * spent between bosses stays in the raid total only. A repeated start for the
	 * boss that is already open keeps that split and moves its start to {@code now}.
	 */
	public void beginSplit(String label, Instant now)
	{
		if (finished || label == null || label.isEmpty() || now == null)
		{
			return;
		}
		if (raidStart == null)
		{
			start(now);
		}
		if (currentLabel != null && ChallengeMessages.sameBoss(currentLabel, label))
		{
			segmentStart = now;
			currentLabel = label;
			ArchiveBoss boss = bossForLabel(label);
			if (boss != null)
			{
				currentBoss = boss;
			}
			return;
		}
		if (currentLabel != null)
		{
			completeSegment(now, null, null);
		}
		currentBoss = bossForLabel(label);
		currentLabel = label;
		segmentStart = now;
		deathCount = 0;
	}

	/**
	 * Closes the split named by a challenge-complete message and stores the game's
	 * duration, raid total, and personal best. A second report for the same boss
	 * fills any missing game times without adding another row.
	 */
	public void completeNamedSplit(String label, Instant now, String gameDuration, String reportedTotal, String personalBest)
	{
		if (label == null || label.isEmpty() || now == null)
		{
			return;
		}
		if (hasSplitNamed(label))
		{
			for (int i = splits.size() - 1; i >= 0; i--)
			{
				Split split = splits.get(i);
				if (ChallengeMessages.sameBoss(split.label, label))
				{
					split.applyGameReport(gameDuration, personalBest);
					break;
				}
			}
			if (reportedTotal != null)
			{
				gameTotal = reportedTotal;
			}
			if (!finished && ChallengeMessages.isOcellus(label))
			{
				finish(now);
			}
			return;
		}
		if (finished)
		{
			return;
		}
		if (raidStart == null)
		{
			start(now);
		}
		boolean hadOpenSplit = currentLabel != null;
		currentLabel = label;
		currentBoss = bossForLabel(label);
		if (!hadOpenSplit || segmentStart == null)
		{
			segmentStart = now;
		}
		completeSegment(now, gameDuration, personalBest);
		if (reportedTotal != null)
		{
			gameTotal = reportedTotal;
		}
		if (ChallengeMessages.isOcellus(label))
		{
			finish(now);
		}
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
			completeSegment(now, null, null);
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
		completeSegment(now, null, null);
		if (finalBoss)
		{
			finish(now);
		}
		return true;
	}

	public void finish(Instant now)
	{
		if (raidStart == null || finished || now == null)
		{
			return;
		}
		if (currentLabel != null)
		{
			completeSegment(now, null, null);
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

	public boolean hasSplitNamed(String label)
	{
		for (int i = 0; i < splits.size(); i++)
		{
			if (ChallengeMessages.sameBoss(splits.get(i).label, label))
			{
				return true;
			}
		}
		return false;
	}

	public boolean hasOpenSplit()
	{
		return currentLabel != null;
	}

	public String getGameTotal()
	{
		return gameTotal;
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
		long hundredths = (millis / 10L) % 100L;
		long totalSeconds = millis / 1000L;
		long seconds = totalSeconds % 60L;
		long minutes = (totalSeconds / 60L) % 60L;
		long hours = totalSeconds / 3600L;
		if (hours > 0L)
		{
			return hours + ":" + pad(minutes) + ":" + pad(seconds) + "." + pad(hundredths);
		}
		return minutes + ":" + pad(seconds) + "." + pad(hundredths);
	}

	private void completeSegment(Instant now, String gameDuration, String personalBest)
	{
		if (now != null && segmentStart != null && currentLabel != null && !currentLabel.isEmpty())
		{
			splits.add(new Split(
				currentBoss,
				currentLabel,
				nonNegative(Duration.between(segmentStart, now)),
				gameDuration,
				personalBest));
		}
		segmentStart = now;
		currentBoss = null;
		currentLabel = null;
		deathCount = 0;
	}

	private static ArchiveBoss bossForLabel(String label)
	{
		ArchiveBoss.BossMatch match = ArchiveBoss.match(label, -1);
		if (match == null)
		{
			return null;
		}
		return match.getBoss();
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
