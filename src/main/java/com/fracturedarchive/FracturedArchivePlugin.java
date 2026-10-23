package com.fracturedarchive;

import com.google.inject.Provides;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Fractured Archive",
	description = "Elapsed-time room splits and a total timer for The Fractured Archive",
	tags = {"raid", "raids", "timer", "splits", "pvm", "archive"}
)
public class FracturedArchivePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private FracturedArchiveConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private FracturedArchiveOverlay overlay;

	private final RaidSession session = new RaidSession();
	private final Map<Integer, ArchiveBoss.BossMatch> tracked = new HashMap<Integer, ArchiveBoss.BossMatch>();
	private final Set<String> loggedNpcKeys = new HashSet<String>();
	private int[] lastRegions;
	private int trackedGeneration;
	private boolean wasRunning;
	private boolean wasFinished;
	private int seenSplits;

	@Override
	protected void startUp() throws Exception
	{
		resetAll();
		overlayManager.add(overlay);
		scanNpcs();
		log.debug("Fractured Archive timer started (showSplits={}). Debug lines prefixed with \"Candidate\" are chat messages and region ids to copy into FracturedArchiveIds.", config.showSplits());
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		resetAll();
		log.debug("Fractured Archive timer stopped");
	}

	@Provides
	FracturedArchiveConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FracturedArchiveConfig.class);
	}

	RaidSession getSession()
	{
		return session;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			resetAll();
			return;
		}
		if (state == GameState.LOGGED_IN)
		{
			scanNpcs();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		WorldView worldView = worldView();
		if (worldView == null)
		{
			return;
		}
		int[] regions = worldView.getMapRegions();
		if (regions == null || regions.length == 0)
		{
			return;
		}
		if (!Arrays.equals(regions, lastRegions))
		{
			lastRegions = Arrays.copyOf(regions, regions.length);
			log.debug("Candidate region IDs: {} (instanced={})", Arrays.toString(lastRegions), worldView.isInstance());
		}
		session.onRegionSample(
			FracturedArchiveIds.hasRegionIds(),
			FracturedArchiveIds.containsRegion(regions),
			worldView.isInstance(),
			Instant.now());
		afterSessionChange("region");
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isCandidateChat(event.getType()))
		{
			return;
		}
		String raw = event.getMessage();
		if (raw == null)
		{
			return;
		}
		String stripped = Text.removeTags(raw);
		if (stripped == null)
		{
			return;
		}
		String message = stripped.replace('\u00A0', ' ').trim();
		if (message.isEmpty())
		{
			return;
		}
		logCandidateChat(event.getType(), message);
		if (!isTimerChat(event.getType()))
		{
			return;
		}

		Instant now = Instant.now();
		if (TextMatch.containsAny(message, FracturedArchiveIds.RAID_START_MESSAGES))
		{
			session.onStartMessage(now, isInstanced());
		}
		if (TextMatch.containsAny(message, FracturedArchiveIds.RAID_COMPLETE_MESSAGES))
		{
			session.onRaidCompleteMessage(now);
		}
		if (TextMatch.containsAny(message, FracturedArchiveIds.ROOM_COMPLETE_MESSAGES))
		{
			session.onRoomCompleteMessage(now);
		}
		ArchiveBoss[] bosses = ArchiveBoss.values();
		for (int i = 0; i < bosses.length; i++)
		{
			if (TextMatch.containsAny(message, bosses[i].getCompletionMessages()))
			{
				session.onBossCompleteMessage(bosses[i], now);
			}
		}
		afterSessionChange("chat");
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		considerSpawn(event.getNpc());
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		considerSpawn(event.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		if (npc == null)
		{
			return;
		}
		ArchiveBoss.BossMatch spawnedAs = tracked.remove(npc.getIndex());
		if (spawnedAs == null)
		{
			return;
		}
		ArchiveBoss.BossMatch current = ArchiveBoss.match(npc.getName(), npc.getId());
		ArchiveBoss.BossMatch match = current != null ? current : spawnedAs;
		boolean dead = isDead(npc);
		log.debug("Tracked NPC despawned name='{}' id={} dead={} countsAsKill={} healthRatio={} healthScale={}",
			npc.getName(), npc.getId(), dead, match.countsAsKill(), npc.getHealthRatio(), npc.getHealthScale());
		if (!dead || !match.countsAsKill())
		{
			return;
		}
		session.onBossDeath(match, Instant.now());
		afterSessionChange("boss-death");
	}

	private void considerSpawn(NPC npc)
	{
		if (npc == null)
		{
			return;
		}
		logCandidateNpc(npc);
		ArchiveBoss.BossMatch match = ArchiveBoss.match(npc.getName(), npc.getId());
		if (match == null)
		{
			return;
		}
		boolean inKnownRegion = inKnownRegion();
		if (!session.acceptsNpc(match, inKnownRegion))
		{
			log.debug("Ignored boss NPC until the raid timer is running. name='{}' id={}. Fill REGION_IDS, RAID_START_MESSAGES, or the NPC id.",
				npc.getName(), npc.getId());
			return;
		}
		int generation = session.getGeneration();
		RaidSession.SpawnResult result = session.onBossSpawn(match, isInstanced(), inKnownRegion, Instant.now());
		if (session.getGeneration() != generation)
		{
			tracked.clear();
			trackedGeneration = session.getGeneration();
		}
		if (result == RaidSession.SpawnResult.TRACK)
		{
			tracked.put(npc.getIndex(), match);
			log.debug("Tracking boss {} (npc id={}, name='{}')",
				match.getBoss().getDisplayName(), npc.getId(), npc.getName());
		}
		afterSessionChange("boss-spawn");
	}

	private void scanNpcs()
	{
		WorldView worldView = worldView();
		if (worldView == null)
		{
			return;
		}
		if (worldView.npcs() == null)
		{
			return;
		}
		for (NPC npc : worldView.npcs())
		{
			considerSpawn(npc);
		}
	}

	private void logCandidateNpc(NPC npc)
	{
		String name = npc.getName();
		if (name == null || !TextMatch.containsAny(name, FracturedArchiveIds.WATCH_KEYWORDS))
		{
			return;
		}
		String key = npc.getId() + "\0" + TextMatch.normalize(name);
		if (!loggedNpcKeys.add(key))
		{
			return;
		}
		WorldView worldView = worldView();
		int[] regions = worldView == null ? null : worldView.getMapRegions();
		boolean instanced = worldView != null && worldView.isInstance();
		log.debug("Candidate NPC name='{}' id={} combatLevel={} regions={} instanced={}",
			name,
			npc.getId(),
			npc.getCombatLevel(),
			Arrays.toString(regions),
			instanced);
	}

	private void logCandidateChat(ChatMessageType type, String message)
	{
		boolean watched = TextMatch.containsAny(message, FracturedArchiveIds.WATCH_KEYWORDS);
		if (!watched && !session.getTimer().isRunning() && !isInstanced())
		{
			return;
		}
		log.debug("Candidate chat [{}]: {}", type, message);
	}

	private void afterSessionChange(String reason)
	{
		int generation = session.getGeneration();
		if (generation != trackedGeneration)
		{
			tracked.clear();
			trackedGeneration = generation;
		}
		RaidTimer timer = session.getTimer();
		if (timer.isRunning() == wasRunning && timer.isFinished() == wasFinished && timer.getSplits().size() == seenSplits)
		{
			return;
		}
		wasRunning = timer.isRunning();
		wasFinished = timer.isFinished();
		seenSplits = timer.getSplits().size();
		log.debug("Timer update ({}): running={} finished={} splits={}", reason, wasRunning, wasFinished, seenSplits);
	}

	private void resetAll()
	{
		session.reset();
		tracked.clear();
		loggedNpcKeys.clear();
		lastRegions = null;
		trackedGeneration = session.getGeneration();
		wasRunning = false;
		wasFinished = false;
		seenSplits = 0;
	}

	private boolean inKnownRegion()
	{
		WorldView worldView = worldView();
		if (worldView == null)
		{
			return false;
		}
		return FracturedArchiveIds.containsRegion(worldView.getMapRegions());
	}

	private boolean isInstanced()
	{
		WorldView worldView = worldView();
		return worldView != null && worldView.isInstance();
	}

	private WorldView worldView()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		return client.getTopLevelWorldView();
	}

	private static boolean isDead(NPC npc)
	{
		if (npc.isDead())
		{
			return true;
		}
		return npc.getHealthScale() > 0 && npc.getHealthRatio() == 0;
	}

	private static boolean isCandidateChat(ChatMessageType type)
	{
		return isTimerChat(type) || type == ChatMessageType.NPC_SAY;
	}

	private static boolean isTimerChat(ChatMessageType type)
	{
		return type == ChatMessageType.GAMEMESSAGE
			|| type == ChatMessageType.SPAM
			|| type == ChatMessageType.MESBOX
			|| type == ChatMessageType.BROADCAST
			|| type == ChatMessageType.FRIENDSCHATNOTIFICATION
			|| type == ChatMessageType.ENGINE
			|| type == ChatMessageType.DIALOG
			|| type == ChatMessageType.TENSECTIMEOUT;
	}
}
