package com.fracturedarchive;

import java.util.Locale;

/**
 * Case-insensitive substring checks used for boss names and chat lines.
 * Apostrophe variants are folded so "Dagon'hai" and "Dagon’hai" match.
 */
public final class TextMatch
{
	private TextMatch()
	{
	}

	public static String normalize(String value)
	{
		if (value == null)
		{
			return "";
		}
		String lower = value.toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replace('\u2018', '\'')
			.replace('`', '\'');
		return lower;
	}

	public static boolean contains(String haystack, String needle)
	{
		if (haystack == null || needle == null)
		{
			return false;
		}
		String trimmed = needle.trim();
		if (trimmed.isEmpty())
		{
			return false;
		}
		return normalize(haystack).contains(normalize(trimmed));
	}

	public static boolean containsAny(String haystack, String[] needles)
	{
		if (haystack == null || needles == null)
		{
			return false;
		}
		for (int i = 0; i < needles.length; i++)
		{
			if (contains(haystack, needles[i]))
			{
				return true;
			}
		}
		return false;
	}
}
