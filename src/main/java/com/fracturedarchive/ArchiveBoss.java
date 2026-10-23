package com.fracturedarchive;

import net.runelite.api.gameval.NpcID;

/**
 * The eight encounters in The Fractured Archive. Each run fights five of them:
 * Archive Guardian, one boss from each choice pair, then Ocellus.
 * <p>
 * Choice pairs are Bork and the Smorks or Ezekiel, Dreadnoth the Jellied or Moia,
 * and Xarth or the Dagon'hai Trio. The overlay records whichever side spawns.
 * <p>
 * An {@link Alias} name is matched as a case-insensitive substring of the NPC name.
 * NPC ids use {@link NpcID} and are preferred over the name. Ids of {@code 0} or
 * less are ignored. {@code deathsToComplete} is how many killing blows end that room.
 * {@link Alias#present} marks a form that means the room is active but does not end it.
 * Smorks, minions, and other adds are omitted so they do not end the room.
 */
public enum ArchiveBoss
{
	/**
	 * First room, every run. The start form only opens the split.
	 * TODO: chat substring that completes this room. If {@code TFA_GUARDIAN_START}
	 * is the form that dies, move it into the kill alias.
	 */
	ARCHIVE_GUARDIAN(
		"Archive Guardian",
		new String[]{
			// TODO: chat substring that completes this room
		},
		Alias.present("Archive Guardian", NpcID.TFA_GUARDIAN_START),
		Alias.kill("Archive Guardian", 1, NpcID.TFA_GUARDIAN, NpcID.TFA_GUARDIAN_UNSHIELDED)
	),

	/**
	 * Second-room choice. Smorks are not listed, so they do not end the room.
	 * TODO: completion chat. Add {@code NpcID.TFA_SMORK} only if the split should wait for them.
	 */
	BORK(
		"Bork and the Smorks",
		new String[]{
			// TODO
		},
		Alias.kill("Bork", 1, NpcID.TFA_BORK)
	),

	/**
	 * Second-room choice.
	 * TODO: completion chat.
	 */
	EZEKIEL(
		"Ezekiel",
		new String[]{
			// TODO
		},
		Alias.kill("Ezekiel", 1, NpcID.TFA_EZEKIEL)
	),

	/**
	 * Third-room choice. The boss NPC is {@code NpcID.TFA_JELLY_BOSS}.
	 * TODO: completion chat, and adjust the name fragment if the in-game name differs.
	 */
	DREADNOTH(
		"Dreadnoth the Jellied",
		new String[]{
			// TODO
		},
		Alias.kill("Dreadnoth", 1, NpcID.TFA_JELLY_BOSS)
	),

	/**
	 * Third-room choice. Moia also exists outside the raid; the name is used only
	 * after a run has started. The NPC id matches anywhere.
	 * TODO: completion chat.
	 */
	MOIA(
		"Moia",
		new String[]{
			// TODO
		},
		Alias.kill("Moia", 1, NpcID.TFA_MOIA)
	),

	/**
	 * Fourth-room choice.
	 * TODO: completion chat.
	 */
	XARTH(
		"Xarth",
		new String[]{
			// TODO
		},
		Alias.kill("Xarth", 1, NpcID.TFA_XARTH)
	),

	/**
	 * Fourth-room choice. Three unshielded deaths end the room (leader and two minions).
	 * The shielded ids only keep the split running.
	 * TODO: completion chat. Move a shielded id into the kill alias if that form is the one that dies.
	 */
	DAGONHAI_TRIO(
		"Dagon'hai Trio",
		new String[]{
			// TODO
		},
		Alias.present("Dagon'hai",
			NpcID.TFA_DAGONHAI_LEADER,
			NpcID.TFA_DAGONHAI_MINION_1,
			NpcID.TFA_DAGONHAI_MINION_2),
		Alias.kill("Dagon'hai", 3,
			NpcID.TFA_DAGONHAI_LEADER_UNSHIELDED,
			NpcID.TFA_DAGONHAI_MINION_1_UNSHIELDED,
			NpcID.TFA_DAGONHAI_MINION_2_UNSHIELDED),
		Alias.kill("Dagonhai", 3)
	),

	/**
	 * Last room, every run. Earlier forms keep one split running. P3's death ends the raid.
	 * TODO: completion chat. Move an earlier id into the kill alias if that form's death is the clear.
	 */
	OCELLUS(
		"Ocellus",
		new String[]{
			// TODO
		},
		Alias.present("Ocellus", NpcID.TFA_OCELLUS_P1, NpcID.TFA_OCELLUS_P2),
		Alias.kill("Ocellus", 1, NpcID.TFA_OCELLUS_P3)
	);

	private final String displayName;
	private final String[] completionMessages;
	private final Alias[] aliases;

	ArchiveBoss(String displayName, String[] completionMessages, Alias... aliases)
	{
		this.displayName = displayName;
		this.completionMessages = completionMessages == null ? new String[0] : completionMessages;
		this.aliases = aliases == null ? new Alias[0] : aliases;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public String[] getCompletionMessages()
	{
		return completionMessages;
	}

	public boolean isFinalBoss()
	{
		return this == OCELLUS;
	}

	public static BossMatch match(String npcName, int npcId)
	{
		ArchiveBoss[] bosses = values();
		for (int i = 0; i < bosses.length; i++)
		{
			Alias[] bossAliases = bosses[i].aliases;
			for (int a = 0; a < bossAliases.length; a++)
			{
				if (bossAliases[a].matchesId(npcId))
				{
					return bossAliases[a].toMatch(bosses[i], true);
				}
			}
		}

		BossMatch best = null;
		int bestLength = -1;
		for (int i = 0; i < bosses.length; i++)
		{
			Alias[] bossAliases = bosses[i].aliases;
			for (int a = 0; a < bossAliases.length; a++)
			{
				Alias alias = bossAliases[a];
				String token = alias.getName();
				if (!TextMatch.contains(npcName, token))
				{
					continue;
				}
				boolean longer = token.length() > bestLength;
				boolean preferKill = token.length() == bestLength && alias.countsAsKill() && best != null && !best.countsAsKill();
				if (best == null || longer || preferKill)
				{
					bestLength = token.length();
					best = alias.toMatch(bosses[i], false);
				}
			}
		}
		return best;
	}

	/**
	 * One way to recognize an encounter. Add further aliases when a room has
	 * more than one NPC name, and pass NPC ids as the trailing numbers.
	 */
	public static final class Alias
	{
		private final String name;
		private final int deathsToComplete;
		private final boolean countsAsKill;
		private final int[] npcIds;

		public Alias(String name, int deathsToComplete, int... npcIds)
		{
			this(name, deathsToComplete, true, npcIds);
		}

		private Alias(String name, int deathsToComplete, boolean countsAsKill, int[] npcIds)
		{
			this.name = name;
			this.deathsToComplete = deathsToComplete;
			this.countsAsKill = countsAsKill;
			this.npcIds = npcIds == null ? new int[0] : npcIds;
		}

		public static Alias kill(String name, int deathsToComplete, int... npcIds)
		{
			return new Alias(name, deathsToComplete, true, npcIds);
		}

		/** A form that shows the room is active. Its despawn does not store a split. */
		public static Alias present(String name, int... npcIds)
		{
			return new Alias(name, 1, false, npcIds);
		}

		public String getName()
		{
			return name;
		}

		public int getDeathsToComplete()
		{
			return deathsToComplete;
		}

		public boolean countsAsKill()
		{
			return countsAsKill;
		}

		private BossMatch toMatch(ArchiveBoss boss, boolean matchedById)
		{
			return new BossMatch(boss, deathsToComplete, matchedById, countsAsKill);
		}

		public boolean matchesId(int npcId)
		{
			if (npcId <= 0)
			{
				return false;
			}
			for (int i = 0; i < npcIds.length; i++)
			{
				if (npcIds[i] > 0 && npcIds[i] == npcId)
				{
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * A spawn or death tied to one encounter.
	 */
	public static final class BossMatch
	{
		private final ArchiveBoss boss;
		private final int deathsRequired;
		private final boolean matchedById;
		private final boolean countsAsKill;

		public BossMatch(ArchiveBoss boss, int deathsRequired, boolean matchedById)
		{
			this(boss, deathsRequired, matchedById, true);
		}

		public BossMatch(ArchiveBoss boss, int deathsRequired, boolean matchedById, boolean countsAsKill)
		{
			this.boss = boss;
			this.deathsRequired = deathsRequired;
			this.matchedById = matchedById;
			this.countsAsKill = countsAsKill;
		}

		public ArchiveBoss getBoss()
		{
			return boss;
		}

		public int getDeathsRequired()
		{
			return deathsRequired;
		}

		public boolean isMatchedById()
		{
			return matchedById;
		}

		public boolean countsAsKill()
		{
			return countsAsKill;
		}
	}
}
