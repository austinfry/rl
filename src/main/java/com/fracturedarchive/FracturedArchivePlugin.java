package com.fracturedarchive;

import com.google.inject.Provides;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
	private OverlayManager overlayManager;

	@Inject
	private FracturedArchiveOverlay overlay;

	private final RaidSession session = new RaidSession();
	private final Map<Integer, ArchiveBoss.BossMatch> tracked = new HashMap<Integer, ArchiveBoss.BossMatch>();
	private int trackedGeneration;

	@Override
	protected void startUp() throws Exception
	{
		resetAll();
		overlayManager.add(overlay);
		scanNpcs();
		log.debug("Fractured Archive timer started");
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
		session.onRegionSample(
			FracturedArchiveIds.hasRegionIds(),
			FracturedArchiveIds.containsRegion(regions),
			worldView.isInstance(),
			Instant.now());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isTimerChat(event.getType()))
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
		if (message.isEmpty() || !TextMatch.normalize(message).startsWith("challenge"))
		{
			return;
		}
		ChallengeMessages.Parsed parsed = ChallengeMessages.parse(message);
		if (parsed == null)
		{
			log.debug("Unmatched Challenge message: {}", message);
			return;
		}
		Instant now = Instant.now();
		if (parsed.isStarted())
		{
			log.debug("Challenge started '{}'", parsed.getBossName());
			session.onChallengeStarted(parsed.getBossName(), now, isInstanced());
		}
		else
		{
			log.debug("Challenge complete '{}' duration={} total={} personalBest={}",
				parsed.getBossName(),
				parsed.getDuration(),
				parsed.getTotal(),
				parsed.getPersonalBest());
			session.onChallengeComplete(
				parsed.getBossName(),
				now,
				parsed.getDuration(),
				parsed.getTotal(),
				parsed.getPersonalBest());
		}
		syncGeneration();
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
		if (!isDead(npc) || !match.countsAsKill())
		{
			return;
		}
		session.onBossDeath(match, Instant.now());
	}

	private void considerSpawn(NPC npc)
	{
		if (npc == null)
		{
			return;
		}
		ArchiveBoss.BossMatch match = ArchiveBoss.match(npc.getName(), npc.getId());
		if (match == null)
		{
			return;
		}
		int generation = session.getGeneration();
		RaidSession.SpawnResult result = session.onBossSpawn(match, isInstanced(), inKnownRegion(), Instant.now());
		if (session.getGeneration() != generation)
		{
			tracked.clear();
			trackedGeneration = session.getGeneration();
		}
		if (result == RaidSession.SpawnResult.TRACK)
		{
			tracked.put(npc.getIndex(), match);
		}
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

	private void syncGeneration()
	{
		int generation = session.getGeneration();
		if (generation != trackedGeneration)
		{
			tracked.clear();
			trackedGeneration = generation;
		}
	}

	private void resetAll()
	{
		session.reset();
		tracked.clear();
		trackedGeneration = session.getGeneration();
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
