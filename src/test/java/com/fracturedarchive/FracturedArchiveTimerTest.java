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
		Assert.assertEquals("0:00.00", RaidTimer.format(Duration.ZERO));
		Assert.assertEquals("1:30.10", RaidTimer.format(Duration.ofMillis(90100)));
		Assert.assertEquals("1:02:03.40", RaidTimer.format(Duration.ofHours(1).plusMinutes(2).plusSeconds(3).plusMillis(400)));
		Assert.assertEquals("0:00.00", RaidTimer.format(Duration.ofMillis(-5)));
		Assert.assertEquals("0:00.00", RaidTimer.format(null));
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
	public void npcIdZeroDoesNotMatch()
	{
		ArchiveBoss.Alias alias = new ArchiveBoss.Alias("Bork", 1, 0, 42);
		Assert.assertFalse(alias.matchesId(0));
		Assert.assertFalse(alias.matchesId(-1));
		Assert.assertTrue(alias.matchesId(42));
	}

	@Test
	public void presenceDoesNotStartTheTimerUntilChallengeStarted()
	{
		RaidSession session = new RaidSession();
		Instant entered = Instant.EPOCH;
		Instant clicked = entered.plusSeconds(45);
		ArchiveBoss.BossMatch guardian = ArchiveBoss.match("Archive Guardian", -1);
		ArchiveBoss.BossMatch guardianById = ArchiveBoss.match(null, NpcID.TFA_GUARDIAN);
		Assert.assertEquals(RaidSession.SpawnResult.IGNORE, session.onBossSpawn(guardian, true, true, entered));
		Assert.assertEquals(RaidSession.SpawnResult.IGNORE, session.onBossSpawn(guardianById, true, false, entered.plusSeconds(1)));
		Assert.assertFalse(session.onBossDeath(guardianById, entered.plusSeconds(2)));
		session.onRegionSample(true, true, true, entered);
		session.onRegionSample(false, false, true, entered.plusSeconds(3));
		session.onChallengeComplete(guardian.getBoss().getDisplayName(), entered.plusSeconds(4), "0:10.00", "0:10.00", "0:09.00");

		RaidTimer timer = session.getTimer();
		Assert.assertTrue(timer.isIdle());
		Assert.assertFalse(timer.isRunning());
		Assert.assertFalse(timer.hasDisplay());
		Assert.assertFalse(timer.hasOpenSplit());
		Assert.assertEquals(0, timer.getSplits().size());
		Assert.assertEquals(Duration.ZERO, timer.getTotalElapsed(clicked));
		Assert.assertFalse(FracturedArchiveOverlay.shouldShow(true, timer.hasDisplay()));

		session.onChallengeStarted("Archive Guardian", clicked, true);
		Assert.assertTrue(timer.isRunning());
		Assert.assertTrue(timer.hasDisplay());
		Assert.assertTrue(FracturedArchiveOverlay.shouldShow(true, timer.hasDisplay()));
		Assert.assertEquals("Archive Guardian", timer.getCurrentLabel());
		Assert.assertEquals(Duration.ofSeconds(10), timer.getTotalElapsed(clicked.plusSeconds(10)));
		Assert.assertEquals(Duration.ZERO, timer.getTotalElapsed(entered.plusSeconds(10)));

		Assert.assertEquals(RaidSession.SpawnResult.IGNORE, session.onBossSpawn(ArchiveBoss.match("Bork", -1), true, true, clicked.plusSeconds(5)));
		Assert.assertFalse(session.onBossDeath(ArchiveBoss.match(null, NpcID.TFA_BORK), clicked.plusSeconds(6)));
		Assert.assertEquals("Archive Guardian", timer.getCurrentLabel());
		Assert.assertEquals(0, timer.getSplits().size());
		Assert.assertTrue(timer.hasOpenSplit());
	}

	@Test
	public void parsesObservedChallengeLines()
	{
		String[] started = {
			"Archive Guardian",
			"Bork",
			"Dreadnoth the Jellied",
			"Xarth",
			"Ocellus",
			"Ezekiel",
			"Moia",
			"Bork and the Smorks",
			"Dagon'hai Trio"
		};
		for (int i = 0; i < started.length; i++)
		{
			ChallengeMessages.Parsed parsed = ChallengeMessages.parse("Challenge started: " + started[i] + ".");
			Assert.assertNotNull(started[i], parsed);
			Assert.assertTrue(parsed.isStarted());
			Assert.assertEquals(started[i], parsed.getBossName());
			Assert.assertNull(parsed.getDuration());
		}

		ChallengeMessages.Parsed guardian = ChallengeMessages.parse(
			"Challenge complete: Archive Guardian. Duration: 2:27.60. Total: 2:27.60. Personal best: 2:23.40");
		Assert.assertNotNull(guardian);
		Assert.assertFalse(guardian.isStarted());
		Assert.assertEquals("Archive Guardian", guardian.getBossName());
		Assert.assertEquals("2:27.60", guardian.getDuration());
		Assert.assertEquals("2:27.60", guardian.getTotal());
		Assert.assertEquals("2:23.40", guardian.getPersonalBest());

		ChallengeMessages.Parsed bork = ChallengeMessages.parse(
			"Challenge complete: Bork. Duration: 2:37.80. Total: 5:24.60. Personal best: 2:35.40");
		Assert.assertEquals("Bork", bork.getBossName());
		Assert.assertEquals("2:37.80", bork.getDuration());
		Assert.assertEquals("5:24.60", bork.getTotal());
		Assert.assertEquals("2:35.40", bork.getPersonalBest());

		ChallengeMessages.Parsed tagged = ChallengeMessages.parse(
			"<col=ff3045>Challenge complete: Bork.</col> Duration: <col=ffffff>2:37.80</col>. Total: 5:24.60. Personal best: 2:35.40.");
		Assert.assertEquals("Bork", tagged.getBossName());
		Assert.assertEquals("2:37.80", tagged.getDuration());
		Assert.assertEquals("5:24.60", tagged.getTotal());
		Assert.assertEquals("2:35.40", tagged.getPersonalBest());

		ChallengeMessages.Parsed shouted = ChallengeMessages.parse(
			"CHALLENGE COMPLETE: XARTH. DURATION: 1:02.00. TOTAL: 3:00.00. PERSONAL BEST: 0:59.00.");
		Assert.assertEquals("XARTH", shouted.getBossName());
		Assert.assertEquals("1:02.00", shouted.getDuration());

		Assert.assertNull(ChallengeMessages.parse("Challenge accepted."));
		Assert.assertNull(ChallengeMessages.parse("Challenge complete: Bork. Nice."));
		Assert.assertNull(ChallengeMessages.parse("You enter the archive."));
		Assert.assertNull(ChallengeMessages.parse("<col=ff0000>Welcome to the Fractured Archive.</col>"));
	}

	@Test
	public void matchesBossNamesLoosely()
	{
		Assert.assertTrue(ChallengeMessages.sameBoss("Bork", "Bork and the Smorks"));
		Assert.assertTrue(ChallengeMessages.sameBoss("dreadnoth the jellied", "Dreadnoth"));
		Assert.assertTrue(ChallengeMessages.sameBoss("Ocellus", "ocellus"));
		Assert.assertFalse(ChallengeMessages.sameBoss("Mo", "Moia"));
		Assert.assertTrue(ChallengeMessages.isOcellus("Ocellus"));
		Assert.assertFalse(ChallengeMessages.isOcellus("Bork"));
	}

	@Test
	public void challengeChatTimesTheFightAndKeepsTheGameReport()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		session.onChallengeStarted("Archive Guardian", start, true);
		Assert.assertTrue(session.getTimer().isRunning());
		Assert.assertTrue(session.getTimer().hasOpenSplit());
		Assert.assertEquals("Archive Guardian", session.getTimer().getCurrentLabel());

		session.onChallengeComplete(
			"Archive Guardian",
			start.plusSeconds(148),
			"2:27.60",
			"2:27.60",
			"2:23.40");
		RaidTimer.Split guardian = session.getTimer().getSplits().get(0);
		Assert.assertEquals("Archive Guardian", guardian.getLabel());
		Assert.assertEquals(Duration.ofSeconds(148), guardian.getDuration());
		Assert.assertEquals("2:27.60", guardian.getGameDuration());
		Assert.assertEquals("2:23.40", guardian.getPersonalBest());
		Assert.assertEquals("2:27.60", session.getTimer().getGameTotal());
		Assert.assertTrue(session.getTimer().isRunning());
		Assert.assertFalse(session.getTimer().hasOpenSplit());

		session.onChallengeStarted("Bork", start.plusSeconds(170), true);
		session.onChallengeComplete(
			"Bork",
			start.plusSeconds(328),
			"2:37.80",
			"5:24.60",
			"2:35.40");
		Assert.assertEquals(2, session.getTimer().getSplits().size());
		Assert.assertEquals(Duration.ofSeconds(158), session.getTimer().getSplits().get(1).getDuration());
		Assert.assertEquals("Bork", session.getTimer().getSplits().get(1).getLabel());
		Assert.assertEquals("5:24.60", session.getTimer().getGameTotal());
		Assert.assertEquals(Duration.ofSeconds(328), session.getTimer().getTotalElapsed(start.plusSeconds(328)));
		Assert.assertEquals(RaidSession.SpawnResult.IGNORE, session.onBossSpawn(ArchiveBoss.match("Xarth", -1), true, false, start.plusSeconds(329)));
		Assert.assertEquals(2, session.getTimer().getSplits().size());
		Assert.assertFalse(session.getTimer().hasOpenSplit());
	}

	@Test
	public void unknownBossNamesStayUsable()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		session.onChallengeStarted("Ezekiel", start, true);
		session.onChallengeComplete("Ezekiel", start.plusSeconds(30), "0:30.00", "0:30.00", "0:28.00");
		session.onChallengeStarted("Moia", start.plusSeconds(40), true);
		session.onChallengeComplete("Moia", start.plusSeconds(70), "0:30.00", "1:10.00", "0:29.00");
		session.onChallengeStarted("Bork and the Smorks", start.plusSeconds(80), true);
		session.onChallengeComplete("Bork and the Smorks", start.plusSeconds(100), "0:20.00", "1:40.00", "0:19.00");
		session.onChallengeStarted("Dagon'hai Trio", start.plusSeconds(110), true);
		session.onChallengeComplete("Dagon'hai Trio", start.plusSeconds(140), "0:30.00", "2:20.00", "0:25.00");
		session.onChallengeStarted("Not Yet Named", start.plusSeconds(150), true);
		Assert.assertEquals("Not Yet Named", session.getTimer().getCurrentLabel());
		session.onChallengeComplete("Not Yet Named", start.plusSeconds(160), "0:10.00", "2:40.00", "0:10.00");

		Assert.assertEquals(5, session.getTimer().getSplits().size());
		Assert.assertEquals("Ezekiel", session.getTimer().getSplits().get(0).getLabel());
		Assert.assertEquals("Dagon'hai Trio", session.getTimer().getSplits().get(3).getLabel());
		Assert.assertEquals("Not Yet Named", session.getTimer().getSplits().get(4).getLabel());
		Assert.assertTrue(session.getTimer().isRunning());
		Assert.assertEquals(Duration.ofSeconds(160), session.getTimer().getTotalElapsed(start.plusSeconds(160)));
	}

	@Test
	public void ocellusCompleteFreezesTheRaid()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		session.onChallengeStarted("Xarth", start, true);
		session.onChallengeComplete("Xarth", start.plusSeconds(20), "0:20.00", "4:00.00", "0:19.00");
		session.onChallengeStarted("Ocellus", start.plusSeconds(30), true);
		session.onChallengeComplete("Ocellus", start.plusSeconds(90), "1:00.00", "5:00.00", "0:55.00");
		Assert.assertTrue(session.getTimer().isFinished());
		Assert.assertEquals("5:00.00", session.getTimer().getGameTotal());
		Assert.assertEquals(Duration.ofSeconds(90), session.getTimer().getTotalElapsed(start.plusSeconds(200)));
		Assert.assertEquals(Duration.ofSeconds(60), session.getTimer().getSplits().get(1).getDuration());
	}

	@Test
	public void duplicateCompleteDoesNotAddASecondSplit()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		session.onChallengeStarted("Xarth", start, true);
		session.onChallengeComplete("Xarth", start.plusSeconds(10), "0:10.00", "0:10.00", "0:09.00");
		session.onChallengeComplete("Xarth", start.plusSeconds(12), "0:10.50", "0:10.50", "0:08.00");
		Assert.assertEquals(1, session.getTimer().getSplits().size());
		Assert.assertEquals("0:10.00", session.getTimer().getSplits().get(0).getGameDuration());
		Assert.assertEquals("0:09.00", session.getTimer().getSplits().get(0).getPersonalBest());
		Assert.assertEquals("0:10.50", session.getTimer().getGameTotal());
		Assert.assertTrue(session.getTimer().isRunning());
	}

	@Test
	public void guardianChallengeAfterAClearStartsANewAttempt()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		session.onChallengeStarted("Archive Guardian", start, true);
		session.onChallengeComplete("Archive Guardian", start.plusSeconds(10), "0:10.00", "0:10.00", "0:09.00");
		session.onChallengeStarted("Ocellus", start.plusSeconds(20), true);
		session.onChallengeComplete("Ocellus", start.plusSeconds(40), "0:20.00", "0:40.00", "0:19.00");
		Assert.assertTrue(session.getTimer().isFinished());

		int generation = session.getGeneration();
		session.onChallengeStarted("Archive Guardian", start.plusSeconds(80), true);
		Assert.assertTrue(session.getGeneration() > generation);
		Assert.assertTrue(session.getTimer().isRunning());
		Assert.assertEquals(0, session.getTimer().getSplits().size());
		Assert.assertEquals("Archive Guardian", session.getTimer().getCurrentLabel());
		Assert.assertNull(session.getTimer().getGameTotal());
	}

	@Test
	public void leavingTheInstanceFreezesAfterGraceTicks()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		session.onRegionSample(false, false, true, start);
		Assert.assertTrue(session.getTimer().isIdle());
		session.onChallengeStarted("Archive Guardian", start, true);
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
	public void configuredRegionDoesNotStartAndLeavingStopsTheTimer()
	{
		RaidSession session = new RaidSession();
		Instant start = Instant.EPOCH;
		session.onRegionSample(true, true, true, start);
		Assert.assertTrue(session.getTimer().isIdle());
		Assert.assertFalse(session.getTimer().hasDisplay());
		Assert.assertFalse(FracturedArchiveOverlay.shouldShow(true, session.getTimer().hasDisplay()));

		session.onChallengeStarted("Archive Guardian", start.plusSeconds(20), true);
		Assert.assertTrue(session.getTimer().isRunning());
		for (int tick = 0; tick < RaidSession.EXIT_GRACE_TICKS; tick++)
		{
			session.onRegionSample(true, false, true, start.plusSeconds(21 + tick));
		}
		Assert.assertTrue(session.getTimer().isFinished());
		session.onRegionSample(true, true, true, start.plusSeconds(40));
		Assert.assertTrue(session.getTimer().isFinished());
		Assert.assertFalse(session.getTimer().isRunning());
		Assert.assertTrue(session.getTimer().hasDisplay());
	}

	@Test
	public void textMatchIgnoresBlankNeedles()
	{
		Assert.assertFalse(TextMatch.containsAny("You enter the archive", new String[0]));
		Assert.assertFalse(TextMatch.containsAny("You enter the archive", new String[]{"", "  "}));
		Assert.assertTrue(TextMatch.containsAny("You enter the Fractured Archive", new String[]{"fractured archive"}));
	}
}
