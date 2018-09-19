package com.basistech.m2e.code.quality.checkstyle.test;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.Test;

import com.basistech.m2e.code.quality.checkstyle.CheckstyleUtil;

public class CheckstyleUtilTest {

	@Test
	public void testAntToCheckstyleConversion() throws Exception {
		if (File.separatorChar == '\\') {
			assertEquals(
					".*\\.java",
					CheckstyleUtil.convertAntStylePatternToCheckstylePattern("**\\*.java"));
		}
		assertEquals(
				"com/foo/.*",
				CheckstyleUtil.convertAntStylePatternToCheckstylePattern("com/foo/"));
		assertEquals(
				"com/foo/.*\\.java",
				CheckstyleUtil.convertAntStylePatternToCheckstylePattern("com/foo/**/*.java"));
		assertEquals(
				".*\\.properties",
				CheckstyleUtil.convertAntStylePatternToCheckstylePattern("**/*.properties"));
	}

}
