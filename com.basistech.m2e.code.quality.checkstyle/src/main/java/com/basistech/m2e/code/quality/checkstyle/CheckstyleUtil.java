package com.basistech.m2e.code.quality.checkstyle;

import java.io.File;

import com.google.common.base.Preconditions;

public final class CheckstyleUtil {

	private static final char WINDOWS_CHAR = '\\';
	private static final String SEPARATOR = "/";
	private static final char SEPARATOR_CHAR = '/';

	private CheckstyleUtil() {
	}

	/**
	 * Helper to convert the maven-checkstyle-plugin includes/excludes pattern
	 * to eclipse checkstyle plugin pattern.
	 * 
	 * @param pattern
	 *            the maven-checkstyle-plugin pattern.
	 * @return the converted checkstyle eclipse pattern.
	 */
	public static String convertAntStylePatternToCheckstylePattern(
	        final String pattern) {
		Preconditions.checkNotNull(pattern, "pattern cannot be null");
		Preconditions.checkArgument(!pattern.isEmpty(),
		        "pattern cannot be empty");

		String sanitizedPattern = File.separatorChar == WINDOWS_CHAR ?
		        pattern.replace(WINDOWS_CHAR, SEPARATOR_CHAR) : pattern;
		final String dupeSeperatorChar = SEPARATOR + SEPARATOR;
		while (sanitizedPattern.contains(dupeSeperatorChar)) {
			sanitizedPattern =
			        sanitizedPattern.replace(dupeSeperatorChar, SEPARATOR);
		}

		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < sanitizedPattern.length(); ++i) {
			final char curChar = sanitizedPattern.charAt(i);
			char nextChar = '\0';
			char nextNextChar = '\0';
			if (i + 1 < sanitizedPattern.length()) {
				nextChar = sanitizedPattern.charAt(i + 1);
			}
			if (i + 2 < sanitizedPattern.length()) {
				nextNextChar = sanitizedPattern.charAt(i + 2);
			}

			if (curChar == '*' && nextChar == '*'
			        && nextNextChar == SEPARATOR_CHAR) {
				sb.append(".*");
				++i;
				++i;
			} else if (curChar == '*') {
				sb.append(".*");
			} else if (curChar == '.') {
				sb.append("\\.");
			} else {
				sb.append(curChar);
			}
		}
		String result = sb.toString();
		if (result.endsWith(SEPARATOR)) {
			result += ".*";
		}

		// cleanup the resulting regex pattern
		while (result.contains(".*.*")) {
			result = result.replace(".*.*", ".*");
		}

		return result;
	}

}
