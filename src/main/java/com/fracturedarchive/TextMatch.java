package com.fracturedarchive;

import java.util.Locale;

/**
 * Case folding for challenge chat. Apostrophe variants are folded so
 * "Dagon'hai" and "Dagon’hai" match.
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
		return value.toLowerCase(Locale.ENGLISH)
			.replace('\u2019', '\'')
			.replace('\u2018', '\'')
			.replace('`', '\'');
	}
}
