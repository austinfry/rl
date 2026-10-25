package com.fracturedarchive;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Room splits plus the running raid total. A completed challenge shows the game's
 * duration, the measured Raid total, and the Boss PB. The game's reported
 * total is labeled Fight total. Hidden when "Show splits" is off or no attempt
 * is being tracked. This overlay draws elapsed time only.
 */
public class FracturedArchiveOverlay extends OverlayPanel
{
	private static final Color FINISHED_TOTAL = new Color(0x55, 0xDD, 0x55);
	private static final Color DIM = new Color(180, 180, 180);
	private static final int PANEL_WIDTH = 300;

	private final FracturedArchivePlugin plugin;
	private final FracturedArchiveConfig config;

	@Inject
	public FracturedArchiveOverlay(FracturedArchivePlugin plugin, FracturedArchiveConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(PRIORITY_LOW);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPreferredSize(new Dimension(PANEL_WIDTH, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		RaidTimer timer = plugin.getSession().getTimer();
		if (!shouldShow(config.showSplits(), timer.hasDisplay()))
		{
			return null;
		}

		Instant now = Instant.now();
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Fractured Archive")
			.color(Color.WHITE)
			.build());

		List<RaidTimer.Split> splits = timer.getSplits();
		for (int i = 0; i < splits.size(); i++)
		{
			RaidTimer.Split split = splits.get(i);
			String measured = RaidTimer.format(split.getDuration());
			String gameDuration = split.getGameDuration();
			if (gameDuration == null)
			{
				panelComponent.getChildren().add(line(split.getLabel(), measured, Color.WHITE));
				continue;
			}
			panelComponent.getChildren().add(line(split.getLabel(), gameDuration, Color.WHITE));
			panelComponent.getChildren().add(line("Raid total", measured, DIM));
			if (split.getPersonalBest() != null)
			{
				panelComponent.getChildren().add(line(personalBestLabel(split.isNewPersonalBest()), split.getPersonalBest(), DIM));
			}
		}

		if (timer.hasOpenSplit())
		{
			panelComponent.getChildren().add(line(
				timer.getCurrentLabel(),
				RaidTimer.format(timer.getCurrentSegmentElapsed(now)),
				Color.YELLOW));
		}

		Color totalColor = timer.isFinished() ? FINISHED_TOTAL : Color.WHITE;
		if (timer.getGameTotal() != null)
		{
			panelComponent.getChildren().add(line("Fight total", timer.getGameTotal(), totalColor));
		}
		panelComponent.getChildren().add(line("Raid total", RaidTimer.format(timer.getTotalElapsed(now)), totalColor));
		return super.render(graphics);
	}

	static boolean shouldShow(boolean showSplits, boolean raidActive)
	{
		return showSplits && raidActive;
	}

	static String personalBestLabel(boolean newPersonalBest)
	{
		return newPersonalBest ? "New Boss PB" : "Boss PB";
	}

	private static LineComponent line(String left, String right, Color color)
	{
		return LineComponent.builder()
			.left(left)
			.right(right)
			.leftColor(color)
			.rightColor(color)
			.build();
	}
}
