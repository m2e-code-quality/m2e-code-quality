/*******************************************************************************
 * Copyright 2010 Mohan KR
 * Copyright 2010 Basis Technology Corp.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.basistech.m2e.code.quality.checkstyle;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.sf.eclipsecs.core.config.ICheckConfiguration;
import net.sf.eclipsecs.core.projectconfig.FileMatchPattern;
import net.sf.eclipsecs.core.projectconfig.FileSet;
import net.sf.eclipsecs.core.projectconfig.ProjectConfigurationWorkingCopy;
import net.sf.eclipsecs.core.util.CheckstylePluginException;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

import com.basistech.m2e.code.quality.shared.AbstractMavenPluginProjectConfigurator;
import com.basistech.m2e.code.quality.shared.MavenPluginWrapper;
import com.basistech.m2e.code.quality.shared.ResourceResolver;

/**
 * Utility class to get checkstyle plugin configuration.
 */
public class MavenPluginConfigurationTranslator {
    private static final String CHECKSTYLE_DEFAULT_CONFIG_LOCATION = "config/sun_checks.xml";
    /** checkstyle maven plugin artifactId */
    private static final Map<String, String> PATTERNS_CACHE =
        new HashMap<String, String>();

    private final MavenProject mavenProject;
    private final IProject project;
    private final URI basedirUri;
    private final AbstractMavenPluginProjectConfigurator configurator;
    private final ResourceResolver resourceResolver;
    private final MavenSession session;
    private final MojoExecution execution;
    
    private MavenPluginConfigurationTranslator(
    		final AbstractMavenPluginProjectConfigurator configurator,
    		final MavenSession session,
    		final MavenProject mavenProject,
    		final MavenPluginWrapper pluginWrapper,
    		final IProject project) throws CoreException {
        	this.mavenProject = mavenProject;
        	this.project = project;
        	this.basedirUri = this.project.getLocationURI();
        	this.resourceResolver = ResourceResolver.newInstance(configurator.getPluginClassRealm(session, 
        			pluginWrapper.getMojoExecution()));
        	this.session = session;
        	this.execution = pluginWrapper.getMojoExecution();
        	this.configurator = configurator;
    }

    public boolean isActive() throws CoreException {
        Boolean isSkip = configurator.getParameterValue("skip",
        		Boolean.class, session, execution);
        return (isSkip != null) ? !isSkip: true;
    }

    public URL getRuleset() 
        throws CheckstylePluginException, CoreException {
        final URL ruleset = this.resourceResolver.resolveLocation(
                this.getConfigLocation());
        if (ruleset == null) {
            throw new CheckstylePluginException(String.format(
               "Failed to resolve RuleSet from configLocation,SKIPPING Eclipse checkstyle configuration"));
        }
        return ruleset;
    }

    public String getHeaderFile()
    	throws CheckstylePluginException, CoreException {
        final URL headerResource = this.resourceResolver.resolveLocation(
            getHeaderLocation());
        if (headerResource == null) {
        	return null;
        }

        String outDir = mavenProject.getBuild().getDirectory();
        File headerFile = new File(outDir, "checkstyle-header.txt");
        copyOut(headerResource, headerFile);

        return headerFile.getAbsolutePath();
    }

    public void updateCheckConfigWithIncludeExcludePatterns(
            final ProjectConfigurationWorkingCopy pcWorkingCopy, final ICheckConfiguration checkCfg) 
        throws CheckstylePluginException, CoreException {
        final FileSet fs = new FileSet("java-sources", checkCfg);
        fs.setEnabled(true);
        //add fileset includes/excludes
        fs.setFileMatchPatterns(this.getIncludesExcludesFileMatchPatterns());
        //now add the config
        pcWorkingCopy.getFileSets().clear();
        pcWorkingCopy.getFileSets().add(fs);
    }

    

    /**
     * Get the {@literal propertiesLocation} element if present in the configuration.
     * 
     * @return the value of the {@code propertyExpansion} element.
     * @throws CoreException 
     */
    protected String getPropertiesLocation() throws CoreException {
    	return configurator.getParameterValue("propertiesLocation",
    			String.class, session, execution);
    }

    /**
     * Get the {@literal propertyExpansion} element if present in the configuration.
     * 
     * @return the value of the {@code propertyExpansion} element.
     * @throws CoreException 
     */
    protected String getPropertyExpansion() throws CoreException {
        return configurator.getParameterValue("propertyExpansion",
        		String.class, session, execution);
    }

    /**
     * Get the {@literal includeTestSourceDirectory} element value if present in the configuration.
     * 
     * @return the value of the {@code includeTestSourceDirectory} element.
     * @throws CoreException 
     */
    public boolean getIncludeTestSourceDirectory() throws CoreException {
    	Boolean includeTestSourceDirectory = configurator.getParameterValue("includeTestSourceDirectory",
    			Boolean.class, session, execution);
    	if (includeTestSourceDirectory != null) {
    		return includeTestSourceDirectory.booleanValue();
    	} else {
    		return false;
    	}
    }
    
    /**
     * Get the {@literal configLocation} element if present in the configuration.
     * 
     * @return the value of the {@code configLocation} element.
     * @throws CoreException 
     */
    private String getConfigLocation() throws CoreException {
        String configLocation = configurator.getParameterValue("configLocation",
        		String.class, session, execution);
        if (configLocation == null) {
            configLocation = CHECKSTYLE_DEFAULT_CONFIG_LOCATION;
        }
        return configLocation;
    }

    private String getHeaderLocation() throws CoreException {
        String configLocation = getConfigLocation();
        String headerLocation = configurator.getParameterValue("headerLocation",
        		String.class, session, execution);
        if ( "config/maven_checks.xml".equals( configLocation )
          && "LICENSE.txt".equals( headerLocation ) ) {
            headerLocation = "config/maven-header.txt";
        }
        return headerLocation;
    }

    private List<String> getIncludes() throws CoreException {
        return this.getPatterns("includes");
    }
    
    private List<String> getExcludes() throws CoreException {
        return this.getPatterns("excludes");
    }
    
    private void copyOut(URL src, File dest) throws CheckstylePluginException {
    	try {
            FileUtils.copyURLToFile(src, dest);
    	} catch (IOException e) {
    		throw new CheckstylePluginException(
              "Failed to resolve header file from configHeader, SKIPPING Eclipse checkstyle configuration");
    	}
    }

    /**
     * 
     * @return                           A list of {@code FileMatchPattern}'s.
     * @throws CheckstylePluginException if an error occurs getting the include
     *                                   exclude patterns.
     * @throws CoreException 
     */
    private List<FileMatchPattern> getIncludesExcludesFileMatchPatterns()
        throws CheckstylePluginException, CoreException {

        final List<FileMatchPattern> patterns = new LinkedList<FileMatchPattern>();
        
        /**
         * Step 1). Get all the source roots (including test sources root, if enabled).
         */
        Set<String> sourceFolders = new HashSet<String>(
                this.mavenProject.getCompileSourceRoots());
        if (getIncludeTestSourceDirectory()) {
            sourceFolders.addAll(this.mavenProject.getTestCompileSourceRoots());
        }
        
        /**
         * Step 2). Get all the includes patterns add them for all source roots.
         * NOTES:
         *  - Since the eclipse-cs excludes override (or more correctly the later ones)
         *    the includes, we make sure that if no include patterns are specified we
         *    at least put the source folders.
         *  - Also, we use relative path to project root.
         */
        final List<String> includePatterns = this.getIncludes();
        for (String folder : sourceFolders) {
            final String folderRelativePath = this.basedirUri
                .relativize(new File(folder).toURI())
                .getPath();
            if (includePatterns.size() != 0) {
                patterns.addAll(this.normalizePatternsToCheckstyleFileMatchPattern(
                				includePatterns, 
                				folderRelativePath, 
                				true));
            } else {
                patterns.add(new FileMatchPattern(folderRelativePath));
            }
        }
        
        /**
         * Step 3). Get all the excludes patterns add them for all source roots.
         * NOTES:
         *  - Since the eclipse-cs excludes override (or more correctly the later ones)
         *    the includes, we do NOT add the sourceFolder to exclude list.
         */
        final List<String> excludePatterns = this.getExcludes();
        for (String folder : sourceFolders) {
	    String folderRelativePath = 
               this.basedirUri.relativize(new File(folder).toURI()).getPath();
            patterns.addAll(this.normalizePatternsToCheckstyleFileMatchPattern(
                    excludePatterns, 
                    this.convertToEclipseCheckstyleRegExpPath(folderRelativePath), 
                    false));
        }
        
        return patterns;
    }
    
    private String convertToEclipseCheckstyleRegExpPath(final String path) {
        String csCompatiblePath = path;
        if (path.endsWith("/")) {
            csCompatiblePath = path.substring(0, path.length() - 1);
        } 
        //we append the .* pattern regardless
        csCompatiblePath = csCompatiblePath + ".*";
        return csCompatiblePath;
    }
    
    private List<FileMatchPattern> normalizePatternsToCheckstyleFileMatchPattern(
            final List<String> patterns, final String relativePath, 
            final boolean setIsIncludePatternFlag) 
            throws CheckstylePluginException {
        List<FileMatchPattern> fileMatchPatterns = new LinkedList<FileMatchPattern>();
        for (String p: patterns) {
            final FileMatchPattern fmp = new FileMatchPattern(
                    String.format("%s%s", relativePath, p));
            fmp.setIsIncludePattern(setIsIncludePatternFlag);
            fileMatchPatterns.add(fmp);
        }
        return fileMatchPatterns;
    }
    
    /**
     * Get the include or exclude patterns from the plugin configuration. 
     * NOTE: this has to be unfortunately transformed, as Maven Plugin configuration supports 
     * ANT style pattern but the Eclipse-CS requires java regex.
     * 
     * @param elemName the parent element name (e.g. {@code <includes> or <excludes>}.
     * @return         a {@code List} of include patterns.
     * @throws CoreException 
     */
    private List<String> getPatterns(String elemName) throws CoreException {
        List<String> transformedPatterns = new LinkedList<String>();
        final String patternsString = configurator.getParameterValue(elemName, String.class, session, 
        		execution);
        if (patternsString == null || patternsString.length() == 0) {
            return transformedPatterns;
        }
        final String[] patternsArray = StringUtils.split(patternsString, ",");
        for (String p : patternsArray) {
            p = StringUtils.strip(p);
            if (p == null || p.length() == 0) {
                continue;
            }
            String csPattern;
            if (PATTERNS_CACHE.containsKey(p)) {
                csPattern = PATTERNS_CACHE.get(p);
            } else {
                csPattern = this.convertAntStylePatternToCheckstylePattern(p);
                PATTERNS_CACHE.put(p, csPattern);
            }
            transformedPatterns.add(csPattern);
        }
        return transformedPatterns;
    }
    
    /**
     * Helper to convert the maven-checkstyle-plugin includes/excludes pattern
     * to eclipse checkstyle plugin pattern.
     * 
     * @param pattern the maven-checkstyle-plugin pattern.
     * @return        the converted checkstyle eclipse pattern.
     */
    private String convertAntStylePatternToCheckstylePattern(final String pattern) {
        if (pattern == null) {
            throw new NullPointerException("pattern cannot be null");
        }
        if (pattern.length() == 0) {
            throw new IllegalArgumentException("pattern cannot empty");
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pattern.length(); ++i) {
            final int nextCharIndex = i + 1;
            //mark the end so we can append the last char of string
            char nextChar = '\0';
            if (nextCharIndex != pattern.length()) {
                nextChar = pattern.charAt(nextCharIndex);
            }
            final char curChar = pattern.charAt(i);
            if (curChar == '*' && nextChar == '*') {
                sb.append(".*");
                ++i;
            } else if (curChar == '*') {
                sb.append(".*");
            } else if (curChar == '.') {
                sb.append("\\.");
            } else {
                sb.append(curChar);
            }
        }
        return sb.toString();
    }
    
	public Properties getConfiguredProperties() throws CoreException, CheckstylePluginException {
		String propertiesLocation = getPropertiesLocation();
		Properties properties = new Properties();
		if (propertiesLocation != null) {
			final URL url = this.resourceResolver.resolveLocation(propertiesLocation);
			if (url == null) {
				throw new CheckstylePluginException(String.format(
						"Failed to resolve propertiesLocation [%s]",
						propertiesLocation));
	        }
			try {
				properties.load(url.openStream());
	        } catch (IOException e) {
	        	throw new CheckstylePluginException(String.format(
	        			"Failed to LOAD properties from propertiesLocation [%s]",
	        			propertiesLocation));
	        }
		}
	    return properties;
	}
	
	public void updatePropertiesWithPropertyExpansion(Properties props)
			throws CheckstylePluginException, CoreException {
		final String propertyExpansion = this.getPropertyExpansion();
		if (propertyExpansion != null) {
			try {
				props.load(new StringReader(propertyExpansion));
			} catch (IOException e) {
				throw new CheckstylePluginException(String.format(
						"[%s]: Failed to checkstyle propertyExpansion [%s]",
						CheckstyleEclipseConstants.LOG_PREFIX,
						propertyExpansion));
			}
      }
	}
    
    public static MavenPluginConfigurationTranslator newInstance(
    		AbstractMavenPluginProjectConfigurator configurator,
    		MavenSession session,
            final MavenProject mavenProject,
            final MavenPluginWrapper mavenPlugin,
            final IProject project) throws CoreException {
        final MavenPluginConfigurationTranslator m2csConverter =
            new MavenPluginConfigurationTranslator(
                    configurator, session, mavenProject,
                    mavenPlugin,
                    project);
        return m2csConverter;
    }




}
