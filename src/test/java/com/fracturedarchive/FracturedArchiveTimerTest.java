package com.fracturedarchive;

import java.time.Duration;
import java.time.Instant;
import net.runelite.api.gameval.NpcID;
import org.junit.Assert;
import org.junit.Test;

public class FracturedArchiveTimerTest
{
	@Test
	public void formatsElapsedTime()
	{
		Assert.assertEquals("0:00.0", RaidTimer.format(Duration.ZERO));
		Assert.assertEquals("1:30.1", RaidTimer.format(Duration.ofMillis(90100)));
		Assert.assertEquals("1:02:03.4", RaidTimer.format(Duration.ofHours(1).plusMinutes(2).plusSeconds(3).plusMillis(400)));
		Assert.assertEquals("0:00.0", RaidTimer.format(Duration.ofMillis(-5)));
		Assert.assertEquals("0:00.0", RaidTimer.format(null));
	}

	@Test
	public void showSplitsDefaultsOnAndHidesTheOverlay()
	{
		FracturedArchiveConfig config = new FracturedArchiveConfig()
		{
		};
		Assert.assertTrue(config.showSplits());
		Assert.assertTrue(FracturedArchiveOverlay.shouldShow(true, true));
		Assert.assertFalse(FracturedArchiveOverlay.shouldShow(false, true));
		Assert.assertFalse(FracturedArchiveOverlay.shouldShow(true, false));
	}

	@Test
	public void matchesBossesByName()
	{
		Assert.assertEquals(ArchiveBoss.ARCHIVE_GUARDIAN, ArchiveBoss.match("The Archive Guardian", -1).getBoss());
		Assert.assertEquals(ArchiveBoss.BORK, ArchiveBoss.match("Bork", 1).getBoss());
		Assert.assertNull(ArchiveBoss.match("Smork", 1));
		Assert.assertEquals(ArchiveBoss.EZEKIEL, ArchiveBoss.match("ezekiel", 1).getBoss());
		Assert.assertEquals(ArchiveBoss.DREADNOTH, ArchiveBoss.match("Dreadnoth the Jellied", 1).getBoss());
		Assert.assertEquals(ArchiveBoss.MOIA, ArchiveBoss.match("Moia", 1).getBoss());
		Assert.assertEquals(ArchiveBoss.XARTH, ArchiveBoss.match("Xarth", 1).getBoss());
		Assert.assertEquals(ArchiveBoss.DAGONHAI_TRIO, ArchiveBoss.match("Dagon'hai Elder", 1).getBoss());
		Assert.assertEquals(ArchiveBoss.DAGONHAI_TRIO, ArchiveBoss.match("Dagon\u2019hai Monk", 1).getBoss());
		Assert.assertEquals(ArchiveBoss.DAGONHAI_TRIO, ArchiveBoss.match("Dagonhai Monk", 1).getBoss());
		Assert.assertEquals(ArchiveBoss.OCELLUS, ArchiveBoss.match("OCELLUS", 1).getBoss());
		Assert.assertNull(ArchiveBoss.match("not-a-boss", Integer.MAX_VALUE));
		Assert.assertNull(ArchiveBoss.match(null, -1));
	}

	@Test
	public void npcIdBeatsTheNameAndIgnoresAdds()
	{
		ArchiveBoss.BossMatch bork = ArchiveBoss.match("Ezekiel", NpcID.TFA_BORK);
		Assert.assertEquals(ArchiveBoss.BORK, bork.getBoss());
		Assert.assertTrue(bork.isMatchedById());
		Assert.assertTrue(bork.countsAsKill());
		Assert.assertNull(ArchiveBoss.match("Smork", NpcID.TFA_SMORK));
		Assert.assertEquals(ArchiveBoss.DREADNOTH, ArchiveBoss.match(null, NpcID.TFA_JELLY_BOSS).getBoss());

		ArchiveBoss.BossMatch earlyOcellus = ArchiveBoss.match("Ocellus", NpcID.TFA_OCELLUS_P1);
		Assert.assertFalse(earlyOcellus.countsAsKill());
		ArchiveBoss.BossMatch lastOcellus = ArchiveBoss.match(null, NpcID.TFA_OCELLUS_P3);
		Assert.assertTrue(lastOcellus.countsAsKill());
		Assert.assertEquals(ArchiveBoss.OCELLUS, lastOcellus.getBoss());
		Assert.assertEquals(3, ArchiveBoss.match(null, NpcID.TFA_DAGONHAI_MINION_2_UNSHIELDED).getDeathsRequired());
		Assert.assertFalse(ArchiveBoss.match(null, NpcID.TFA_DAGONHAI_LEADER).countsAsKill());
	}

	@Test
	public void ocellusEarlyFormsDoNotEndTheRaid()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		ArchiveBoss.BossMatch phaseOne = ArchiveBoss.match(null, NpcID.TFA_OCELLUS_P1);
		ArchiveBoss.BossMatch phaseThree = ArchiveBoss.match(null, NpcID.TFA_OCELLUS_P3);
		session.onBossSpawn(phaseOne, true, false, start);
		session.onBossDeath(phaseOne, start.plusSeconds(10));
		Assert.assertEquals(0, session.getTimer().getSplits().size());
		Assert.assertTrue(session.getTimer().isRunning());
		session.onBossSpawn(phaseThree, true, false, start.plusSeconds(11));
		session.onBossDeath(phaseThree, start.plusSeconds(40));
		Assert.assertTrue(session.getTimer().isFinished());
		Assert.assertEquals(1, session.getTimer().getSplits().size());
		Assert.assertEquals(Duration.ofSeconds(40), session.getTimer().getSplits().get(0).getDuration());
	}

	@Test
	public void npcIdZeroDoesNotMatch()
	{
		ArchiveBoss.Alias alias = new ArchiveBoss.Alias("Bork", 1, 0, 42);
		Assert.assertFalse(alias.matchesId(0));
		Assert.assertFalse(alias.matchesId(-1));
		Assert.assertTrue(alias.matchesId(42));
	}

	@Test
	public void configuredNpcIdStartsAnywhere()
	{
		RaidSession session = new RaidSession();
		ArchiveBoss.BossMatch byId = new ArchiveBoss.BossMatch(ArchiveBoss.BORK, 1, true);
		Assert.assertTrue(session.acceptsNpc(byId, false));
		Assert.assertFalse(session.acceptsNpc(ArchiveBoss.match("Bork", 1), false));
		Assert.assertTrue(session.acceptsNpc(ArchiveBoss.match("Archive Guardian", 1), false));
	}

	@Test
	public void recordsFiveRoomsAndFreezesOnOcellus()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		fight(session, "Archive Guardian", start, 60);
		fight(session, "Bork", start.plusSeconds(60), 40);
		fight(session, "Dreadnoth the Jellied", start.plusSeconds(100), 50);
		fight(session, "Xarth", start.plusSeconds(150), 30);
		fight(session, "Ocellus", start.plusSeconds(180), 90);

		RaidTimer timer = session.getTimer();
		Assert.assertTrue(timer.isFinished());
		Assert.assertEquals(5, timer.getSplits().size());
		Assert.assertEquals("Archive Guardian", timer.getSplits().get(0).getLabel());
		Assert.assertEquals("Bork and the Smorks", timer.getSplits().get(1).getLabel());
		Assert.assertEquals("Dreadnoth the Jellied", timer.getSplits().get(2).getLabel());
		Assert.assertEquals("Xarth", timer.getSplits().get(3).getLabel());
		Assert.assertEquals("Ocellus", timer.getSplits().get(4).getLabel());
		Assert.assertEquals(Duration.ofSeconds(60), timer.getSplits().get(0).getDuration());
		Assert.assertEquals(Duration.ofSeconds(40), timer.getSplits().get(1).getDuration());
		Assert.assertEquals(Duration.ofSeconds(270), timer.getTotalElapsed(start.plusSeconds(999)));
	}

	@Test
	public void alternateChoicesCanBeFoughtInPairOrder()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		fight(session, "Archive Guardian", start, 10);
		fight(session, "Ezekiel", start.plusSeconds(10), 10);
		fight(session, "Moia", start.plusSeconds(20), 10);
		ArchiveBoss.BossMatch trio = ArchiveBoss.match("Dagon'hai Elder", -1);
		Assert.assertNotNull(trio);
		session.onBossSpawn(trio, true, false, start.plusSeconds(30));
		for (int death = 1; death < trio.getDeathsRequired(); death++)
		{
			session.onBossDeath(trio, start.plusSeconds(30 + death));
			Assert.assertEquals(3, session.getTimer().getSplits().size());
		}
		session.onBossDeath(trio, start.plusSeconds(30 + trio.getDeathsRequired()));
		Assert.assertEquals(4, session.getTimer().getSplits().size());
		Assert.assertEquals("Dagon'hai Trio", session.getTimer().getSplits().get(3).getLabel());
		Assert.assertTrue(session.getTimer().isRunning());

		fight(session, "Ocellus", start.plusSeconds(30 + trio.getDeathsRequired()), 10);
		Assert.assertTrue(session.getTimer().isFinished());
		Assert.assertEquals(5, session.getTimer().getSplits().size());
	}

	@Test
	public void betweenRoomsCountsTowardTheNextSplit()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		ArchiveBoss.BossMatch guardian = ArchiveBoss.match("Archive Guardian", -1);
		ArchiveBoss.BossMatch ezekiel = ArchiveBoss.match("Ezekiel", -1);
		session.onBossSpawn(guardian, true, false, start);
		session.onBossDeath(guardian, start.plusSeconds(60));
		Assert.assertEquals("Between rooms", session.getTimer().getCurrentLabel());
		session.onBossSpawn(ezekiel, true, false, start.plusSeconds(80));
		session.onBossDeath(ezekiel, start.plusSeconds(140));
		Assert.assertEquals(Duration.ofSeconds(80), session.getTimer().getSplits().get(1).getDuration());
	}

	@Test
	public void phaseRespawnDoesNotOpenASecondSplit()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		ArchiveBoss.BossMatch guardian = ArchiveBoss.match("Archive Guardian", -1);
		session.onBossSpawn(guardian, true, false, start);
		Assert.assertEquals(RaidSession.SpawnResult.TRACK, session.onBossSpawn(guardian, true, false, start.plusSeconds(5)));
		Assert.assertEquals(0, session.getTimer().getSplits().size());
		session.onBossDeath(guardian, start.plusSeconds(20));
		Assert.assertEquals(1, session.getTimer().getSplits().size());
		Assert.assertEquals(Duration.ofSeconds(20), session.getTimer().getSplits().get(0).getDuration());
		Assert.assertEquals(RaidSession.SpawnResult.IGNORE, session.onBossSpawn(guardian, true, false, start.plusSeconds(25)));
	}

	@Test
	public void ocellusRespawnAfterTheClearKeepsTheFinishedTime()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		ArchiveBoss.BossMatch ocellus = ArchiveBoss.match(null, NpcID.TFA_OCELLUS_P3);
		Assert.assertEquals(RaidSession.SpawnResult.TRACK, session.onBossSpawn(ocellus, true, false, start));
		session.onBossDeath(ocellus, start.plusSeconds(20));
		Assert.assertTrue(session.getTimer().isFinished());
		ArchiveBoss.BossMatch again = ArchiveBoss.match(null, NpcID.TFA_OCELLUS_P3);
		Assert.assertEquals(RaidSession.SpawnResult.IGNORE, session.onBossSpawn(again, true, false, start.plusSeconds(25)));
		Assert.assertEquals(1, session.getTimer().getSplits().size());
		Assert.assertEquals(Duration.ofSeconds(20), session.getTimer().getTotalElapsed(start.plusSeconds(25)));
	}

	@Test
	public void guardianAfterAFinishedRaidStartsANewAttempt()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		fight(session, "Archive Guardian", start, 15);
		fight(session, "Ocellus", start.plusSeconds(15), 15);
		Assert.assertTrue(session.getTimer().isFinished());

		int generation = session.getGeneration();
		ArchiveBoss.BossMatch guardian = ArchiveBoss.match("Archive Guardian", -1);
		Assert.assertEquals(RaidSession.SpawnResult.TRACK, session.onBossSpawn(guardian, true, false, start.plusSeconds(40)));
		Assert.assertTrue(session.getGeneration() > generation);
		Assert.assertTrue(session.getTimer().isRunning());
		Assert.assertEquals(0, session.getTimer().getSplits().size());
		Assert.assertEquals(ArchiveBoss.ARCHIVE_GUARDIAN, session.getTimer().getCurrentBoss());
	}

	@Test
	public void startMessageKeepsTimeUntilTheFirstBoss()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		session.onStartMessage(start, true);
		session.onStartMessage(start.plusSeconds(10), true);
		ArchiveBoss.BossMatch guardian = ArchiveBoss.match("Archive Guardian", -1);
		session.onBossSpawn(guardian, true, false, start.plusSeconds(30));
		session.onBossDeath(guardian, start.plusSeconds(90));
		Assert.assertEquals(Duration.ofSeconds(90), session.getTimer().getSplits().get(0).getDuration());
		Assert.assertEquals(Duration.ofSeconds(90), session.getTimer().getTotalElapsed(start.plusSeconds(90)));
	}

	@Test
	public void roomCompleteMessageClosesTheCurrentRoom()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		ArchiveBoss.BossMatch guardian = ArchiveBoss.match("Archive Guardian", -1);
		session.onBossSpawn(guardian, true, false, start);
		session.onRoomCompleteMessage(start.plusSeconds(45));
		Assert.assertEquals(1, session.getTimer().getSplits().size());
		Assert.assertEquals(Duration.ofSeconds(45), session.getTimer().getSplits().get(0).getDuration());
		Assert.assertTrue(session.getTimer().isRunning());
		session.onRoomCompleteMessage(start.plusSeconds(50));
		Assert.assertEquals(1, session.getTimer().getSplits().size());
	}

	@Test
	public void leavingTheInstanceFreezesAfterGraceTicks()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		session.onBossSpawn(ArchiveBoss.match("Archive Guardian", -1), true, false, start);
		for (int tick = 0; tick < RaidSession.EXIT_GRACE_TICKS - 1; tick++)
		{
			session.onRegionSample(false, false, false, start.plusSeconds(tick + 1));
		}
		Assert.assertTrue(session.getTimer().isRunning());
		session.onRegionSample(false, false, true, start.plusSeconds(10));
		Assert.assertTrue(session.getTimer().isRunning());
		for (int tick = 0; tick < RaidSession.EXIT_GRACE_TICKS; tick++)
		{
			session.onRegionSample(false, false, false, start.plusSeconds(20 + tick));
		}
		Assert.assertTrue(session.getTimer().isFinished());
		Assert.assertEquals(1, session.getTimer().getSplits().size());
	}

	@Test
	public void configuredRegionStartsAndStopsTheTimer()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		session.onRegionSample(true, true, true, start);
		Assert.assertTrue(session.getTimer().isRunning());
		Assert.assertEquals("Current", session.getTimer().getCurrentLabel());
		for (int tick = 0; tick < RaidSession.EXIT_GRACE_TICKS; tick++)
		{
			session.onRegionSample(true, false, true, start.plusSeconds(tick + 1));
		}
		Assert.assertTrue(session.getTimer().isFinished());
		session.onRegionSample(true, true, true, start.plusSeconds(30));
		Assert.assertTrue(session.getTimer().isFinished());
		Assert.assertFalse(session.getTimer().isRunning());
	}

	@Test
	public void textMatchIgnoresBlankNeedles()
	{
		Assert.assertFalse(TextMatch.containsAny("You enter the archive", new String[0]));
		Assert.assertFalse(TextMatch.containsAny("You enter the archive", new String[]{"", "  "}));
		Assert.assertTrue(TextMatch.containsAny("You enter the Fractured Archive", new String[]{"fractured archive"}));
	}

	private static void fight(RaidSession session, String npcName, Instant start, int seconds)
	{
		ArchiveBoss.BossMatch match = ArchiveBoss.match(npcName, -1);
		Assert.assertNotNull(npcName, match);
		Assert.assertEquals(RaidSession.SpawnResult.TRACK, session.onBossSpawn(match, true, false, start));
		session.onBossDeath(match, start.plusSeconds(seconds));
	}
}
