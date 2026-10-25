package com.fracturedarchive;

import java.time.Instant;

/**
 * Starts and ends raid time from challenge chat only. Leaving the instance
 * freezes a timer that is already running.
 */
public final class RaidSession
{
	/**
	 * Game ticks spent outside the raid before a running timer freezes.
	 * A short grace period skips one-tick region or instance flicker.
	 */
	public static final int EXIT_GRACE_TICKS = 5;

	private static final String ARCHIVE_GUARDIAN = "Archive Guardian";

	private final RaidTimer timer = new RaidTimer();
	private boolean armedInstanceExit;
	private int outsideTicks;
	private int generation;

	public RaidTimer getTimer()
	{
		return timer;
	}

	public int getGeneration()
	{
		return generation;
	}

	public void reset()
	{
		timer.reset();
		armedInstanceExit = false;
		outsideTicks = 0;
		generation++;
	}

	/**
	 * A {@code Challenge started} line opens that boss's split and starts the raid
	 * clock on the first one. Archive Guardian after that room is already stored
	 * starts a new attempt, as does any challenge start after the raid is finished.
	 */
	public void onChallengeStarted(String bossName, Instant now, boolean instanced)
	{
		if (bossName == null || bossName.isEmpty() || now == null)
		{
			return;
		}
		if (startsNewAttempt(bossName))
		{
			reset();
		}
		timer.beginSplit(bossName, now);
		if (instanced && timer.isRunning())
		{
			armedInstanceExit = true;
		}
	}

	/**
	 * A {@code Challenge complete} line closes that boss's split and keeps the
	 * game's duration, total, and personal best.
	 */
	public void onChallengeComplete(String bossName, Instant now, String duration, String total, String personalBest)
	{
		onChallengeComplete(bossName, now, duration, total, personalBest, false);
	}

	public void onChallengeComplete(String bossName, Instant now, String duration, String total, String personalBest, boolean newPersonalBest)
	{
		if (bossName == null || bossName.isEmpty() || now == null)
		{
			return;
		}
		timer.completeNamedSplit(bossName, now, duration, total, personalBest, newPersonalBest);
	}

	/**
	 * While a raid is running inside an instance, leaving that instance for
	 * {@link #EXIT_GRACE_TICKS} ticks freezes the timer.
	 */
	public void onInstanceSample(boolean instanced, Instant now)
	{
		if (now == null)
		{
			return;
		}
		if (timer.isRunning() && instanced)
		{
			armedInstanceExit = true;
			outsideTicks = 0;
			return;
		}
		if (timer.isRunning() && armedInstanceExit && !instanced)
		{
			outsideTicks++;
			if (outsideTicks >= EXIT_GRACE_TICKS)
			{
				timer.finish(now);
				outsideTicks = 0;
			}
			return;
		}
		outsideTicks = 0;
	}

	private boolean startsNewAttempt(String bossName)
	{
		if (timer.isFinished())
		{
			return true;
		}
		if (!ChallengeMessages.sameBoss(bossName, ARCHIVE_GUARDIAN))
		{
			return false;
		}
		if (!timer.hasSplitNamed(ARCHIVE_GUARDIAN))
		{
			return false;
		}
		return !timer.hasOpenSplit() || !ChallengeMessages.sameBoss(bossName, timer.getCurrentLabel());
	}
}
