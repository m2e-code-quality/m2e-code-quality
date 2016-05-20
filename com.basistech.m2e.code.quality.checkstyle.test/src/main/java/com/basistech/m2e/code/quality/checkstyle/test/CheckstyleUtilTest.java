package com.basistech.m2e.code.quality.checkstyle.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.basistech.m2e.code.quality.checkstyle.CheckstyleUtil;

public class CheckstyleUtilTest {

	@Test
	public void testAntToCheckstyleConversion() throws Exception {
		assertEquals(".*\\.java", CheckstyleUtil
		        .convertAntStylePatternToCheckstylePattern("**\\/*.java"));
		assertEquals(".*\\.properties", CheckstyleUtil
		        .convertAntStylePatternToCheckstylePattern("**/*.properties"));
	}

}
