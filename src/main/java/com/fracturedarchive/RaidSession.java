package com.fracturedarchive;

import java.time.Instant;

/**
 * Decides when a raid attempt starts, when a room split is stored, and when the
 * total freezes. Challenge chat is authoritative once any challenge line is seen.
 * Until then, NPC names and optional region ids are the fallback. NPC name matches
 * for bosses other than the Archive Guardian are used only after a run has started,
 * so overworld NPCs with the same name are left alone. A configured NPC id matches anywhere.
 */
public final class RaidSession
{
	/**
	 * Game ticks spent outside the raid before a running timer freezes.
	 * A short grace period skips one-tick region or instance flicker.
	 */
	public static final int EXIT_GRACE_TICKS = 5;

	public enum SpawnResult
	{
		IGNORE,
		TRACK
	}

	private final RaidTimer timer = new RaidTimer();
	private boolean chatAuthoritative;
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
		chatAuthoritative = false;
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
		chatAuthoritative = true;
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
		if (bossName == null || bossName.isEmpty() || now == null)
		{
			return;
		}
		chatAuthoritative = true;
		timer.completeNamedSplit(bossName, now, duration, total, personalBest);
	}

	public boolean acceptsNpc(ArchiveBoss.BossMatch match, boolean inKnownRegion)
	{
		if (match == null)
		{
			return false;
		}
		if (match.isMatchedById() || inKnownRegion || timer.isRunning())
		{
			return true;
		}
		return match.getBoss() == ArchiveBoss.ARCHIVE_GUARDIAN;
	}

	public SpawnResult onBossSpawn(ArchiveBoss.BossMatch match, boolean instanced, boolean inKnownRegion, Instant now)
	{
		if (chatAuthoritative || !acceptsNpc(match, inKnownRegion) || now == null)
		{
			return SpawnResult.IGNORE;
		}
		if (startsNewRaid(match))
		{
			reset();
		}
		if (timer.isFinished())
		{
			return SpawnResult.IGNORE;
		}
		if (timer.hasSplitFor(match.getBoss()) && timer.getCurrentBoss() != match.getBoss())
		{
			return SpawnResult.IGNORE;
		}
		if (timer.isIdle())
		{
			timer.start(now);
		}
		if (instanced)
		{
			armedInstanceExit = true;
		}
		timer.enterBoss(match.getBoss(), match.getBoss().getDisplayName(), now);
		return SpawnResult.TRACK;
	}

	/**
	 * @return true when the death completed the room
	 */
	public boolean onBossDeath(ArchiveBoss.BossMatch match, Instant now)
	{
		if (chatAuthoritative || match == null || now == null || timer.isFinished() || !match.countsAsKill())
		{
			return false;
		}
		if (timer.hasSplitFor(match.getBoss()) && timer.getCurrentBoss() != match.getBoss())
		{
			return false;
		}
		return timer.recordDeath(match.getBoss(), match.getBoss().getDisplayName(), match.getDeathsRequired(), now);
	}

	public void onRegionSample(boolean regionsConfigured, boolean inKnownRegion, boolean instanced, Instant now)
	{
		if (now == null)
		{
			return;
		}
		if (regionsConfigured)
		{
			if (inKnownRegion)
			{
				outsideTicks = 0;
				if (timer.isIdle())
				{
					timer.start(now);
					if (instanced)
					{
						armedInstanceExit = true;
					}
				}
				return;
			}
			if (timer.isRunning())
			{
				outsideTicks++;
				if (outsideTicks >= EXIT_GRACE_TICKS)
				{
					timer.finish(now);
					outsideTicks = 0;
				}
			}
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
		if (!ChallengeMessages.sameBoss(bossName, ArchiveBoss.ARCHIVE_GUARDIAN.getDisplayName()))
		{
			return false;
		}
		if (!timer.hasSplitNamed(ArchiveBoss.ARCHIVE_GUARDIAN.getDisplayName()))
		{
			return false;
		}
		return !timer.hasOpenSplit() || !ChallengeMessages.sameBoss(bossName, timer.getCurrentLabel());
	}

	private boolean startsNewRaid(ArchiveBoss.BossMatch match)
	{
		if (!timer.isFinished() || timer.getCurrentBoss() == match.getBoss())
		{
			return false;
		}
		if (match.getBoss() == ArchiveBoss.ARCHIVE_GUARDIAN)
		{
			return true;
		}
		if (!match.isMatchedById() || timer.getSplits().isEmpty())
		{
			return match.isMatchedById();
		}
		ArchiveBoss lastBoss = timer.getSplits().get(timer.getSplits().size() - 1).getBoss();
		return lastBoss != match.getBoss();
	}
}
