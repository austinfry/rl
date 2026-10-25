package com.fracturedarchive;

/**
 * Parses Fractured Archive challenge lines from the game chat.
 * <p>
 * {@code Challenge started: <Boss>.} opens a split.
 * {@code Challenge complete: <Boss>. Duration: m:ss.xx. Total: m:ss.xx. Personal best: m:ss.xx.}
 * closes it. The personal-best section may be omitted. A trailing {@code (new personal best)}
 * means the Duration just recorded is the new personal best. Names are matched loosely;
 * the text from the message is kept as the label.
 */
public final class ChallengeMessages
{
	static final String STARTED_LEAD = "challenge started";
	static final String COMPLETE_LEAD = "challenge complete";

	private ChallengeMessages()
	{
	}

	public static final class Parsed
	{
		private final boolean started;
		private final String bossName;
		private final String duration;
		private final String total;
		private final String personalBest;
		private final boolean newPersonalBest;

		private Parsed(boolean started, String bossName, String duration, String total, String personalBest, boolean newPersonalBest)
		{
			this.started = started;
			this.bossName = bossName;
			this.duration = duration;
			this.total = total;
			this.personalBest = personalBest;
			this.newPersonalBest = newPersonalBest;
		}

		public boolean isStarted()
		{
			return started;
		}

		public String getBossName()
		{
			return bossName;
		}

		public String getDuration()
		{
			return duration;
		}

		public String getTotal()
		{
			return total;
		}

		public String getPersonalBest()
		{
			return personalBest;
		}

		public boolean isNewPersonalBest()
		{
			return newPersonalBest;
		}
	}

	/**
	 * @return the parsed line, or null when {@code raw} is not a challenge start or complete message
	 */
	public static Parsed parse(String raw)
	{
		String message = stripTags(raw);
		if (message.isEmpty())
		{
			return null;
		}
		String lower = TextMatch.normalize(message);
		int startedEnd = leadEnd(lower, STARTED_LEAD);
		if (startedEnd >= 0)
		{
			String name = cleanBossName(message.substring(startedEnd));
			if (name.isEmpty())
			{
				return null;
			}
			return new Parsed(true, name, null, null, null, false);
		}
		int completeEnd = leadEnd(lower, COMPLETE_LEAD);
		if (completeEnd < 0)
		{
			return null;
		}
		return parseComplete(message, lower, completeEnd);
	}

	public static boolean sameBoss(String left, String right)
	{
		String a = fold(left);
		String b = fold(right);
		if (a.isEmpty() || b.isEmpty())
		{
			return false;
		}
		if (a.equals(b))
		{
			return true;
		}
		return a.length() >= 4 && b.length() >= 4 && (a.contains(b) || b.contains(a));
	}

	public static boolean isOcellus(String bossName)
	{
		return sameBoss(bossName, "Ocellus");
	}

	private static Parsed parseComplete(String message, String lower, int nameStart)
	{
		int durationAt = lower.indexOf("duration:", nameStart);
		int totalAt = lower.indexOf("total:", nameStart);
		if (durationAt < 0 || totalAt < 0 || durationAt > totalAt)
		{
			return null;
		}
		String name = cleanBossName(message.substring(nameStart, durationAt));
		String duration = timeToken(message.substring(durationAt + "duration:".length(), totalAt));
		String afterTotal = message.substring(totalAt + "total:".length());
		String lowerAfterTotal = lower.substring(totalAt + "total:".length());
		String total = timeToken(afterTotal);
		if (name.isEmpty() || duration.isEmpty() || total.isEmpty())
		{
			return null;
		}
		String personalBest = null;
		int personalBestAt = indexOfPersonalBestTime(lowerAfterTotal);
		if (personalBestAt >= 0)
		{
			String parsedBest = timeToken(afterTotal.substring(personalBestAt + "personal best:".length()));
			if (!parsedBest.isEmpty())
			{
				personalBest = parsedBest;
			}
		}
		boolean newPersonalBest = lowerAfterTotal.contains("new personal best");
		if (newPersonalBest && personalBest == null)
		{
			personalBest = duration;
		}
		return new Parsed(false, name, duration, total, personalBest, newPersonalBest);
	}

	/**
	 * Finds {@code Personal best:} when it introduces a time. {@code (new personal best)} has no colon,
	 * so it is not this section.
	 */
	private static int indexOfPersonalBestTime(String lowerAfterTotal)
	{
		int from = 0;
		while (from < lowerAfterTotal.length())
		{
			int at = lowerAfterTotal.indexOf("personal best:", from);
			if (at < 0)
			{
				return -1;
			}
			boolean newRecord = at >= 4 && lowerAfterTotal.startsWith("new ", at - 4);
			if (!newRecord)
			{
				return at;
			}
			from = at + "personal best:".length();
		}
		return -1;
	}

	private static int leadEnd(String lower, String lead)
	{
		if (!lower.startsWith(lead))
		{
			return -1;
		}
		int index = lead.length();
		while (index < lower.length() && lower.charAt(index) == ' ')
		{
			index++;
		}
		if (index < lower.length() && lower.charAt(index) == ':')
		{
			return index + 1;
		}
		return -1;
	}

	private static String cleanBossName(String value)
	{
		String trimmed = value.trim();
		while (trimmed.endsWith("."))
		{
			trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
		}
		return trimmed;
	}

	/**
	 * Reads an {@code m:ss.xx} token. A second dot is the sentence period after the time.
	 */
	private static String timeToken(String value)
	{
		String trimmed = value.trim();
		StringBuilder token = new StringBuilder();
		boolean seenDot = false;
		for (int i = 0; i < trimmed.length(); i++)
		{
			char c = trimmed.charAt(i);
			if (c >= '0' && c <= '9' || c == ':')
			{
				token.append(c);
				continue;
			}
			if (c == '.' && !seenDot)
			{
				token.append(c);
				seenDot = true;
				continue;
			}
			break;
		}
		return token.toString();
	}

	private static String fold(String value)
	{
		if (value == null)
		{
			return "";
		}
		return TextMatch.normalize(cleanBossName(value));
	}

	static String stripTags(String raw)
	{
		if (raw == null)
		{
			return "";
		}
		StringBuilder builder = new StringBuilder(raw.length());
		boolean inTag = false;
		for (int i = 0; i < raw.length(); i++)
		{
			char c = raw.charAt(i);
			if (c == '<')
			{
				inTag = true;
				continue;
			}
			if (c == '>' && inTag)
			{
				inTag = false;
				continue;
			}
			if (!inTag)
			{
				builder.append(c == '\u00A0' ? ' ' : c);
			}
		}
		return builder.toString().trim();
	}
}
