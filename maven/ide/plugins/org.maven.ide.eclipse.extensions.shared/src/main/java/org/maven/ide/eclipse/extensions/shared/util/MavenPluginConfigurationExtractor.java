package org.maven.ide.eclipse.extensions.shared.util;

import java.util.LinkedList;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import com.google.common.collect.ImmutableList;


/**
 * A helper class to extract elements from a plugin {@literal configuration}
 * element.
 * 
 * <p>
 *  <h3>Usage</h3>
 *  <pre>
 *    final MavenPluginConfigurationExtractor configExtractor =
 *        MavenPluginConfigurationExtractor.newInstance(Xpp3Dom configuration);
 *  </pre>
 *  <code>
 *  
 * </p>
 * 
 */
public class MavenPluginConfigurationExtractor {
    private static final String M2ECLIPSE_CONFIG_ELEM_NAME = "m2eclipseConfig";
    private final Xpp3Dom configuration;
    private final Xpp3Dom m2eConfig;
    
    private MavenPluginConfigurationExtractor(final Xpp3Dom configuration, final Xpp3Dom m2eConfig) {
        this.configuration = configuration;
        this.m2eConfig = m2eConfig;
    }

    /**
     * Retrieve the value of child element of the plugin {@code configuration} element
     * as {@code String}.
     * 
     * @param dom      the parent {@link Xpp3Dom} element. If {@code null} it will use
     *                 the {@literal configuration} DOM.
     * @param elemName the name of the child element of {@code configuration} element.
     * @return         the {@code String} value or {@code null} if child element does not
     *                 exist.
     */
    public boolean asBoolean(final Xpp3Dom dom, final String elemName) {
        return Boolean.parseBoolean(this.value(dom, elemName));
    }
    
    /**
     * Retrieve the value of child element of the plugin {@code dom} element
     * as {@code String}.
     * 
     * @param dom      the parent {@link Xpp3Dom} element. If {@code null} it will use
     *                 the {@literal configuration} DOM.
     * @param elemName the name of the child element of {@code configuration} element.
     * @return         the {@code String} value or {@code null} if child element does not
     *                 exist.
     */
    public String value(final Xpp3Dom dom, final String elemName) {
        final Xpp3Dom cfgDom = dom == null ? this.configuration : dom;
        final Xpp3Dom elemValue = cfgDom.getChild(elemName);
        return elemValue == null ? null : StringUtils.strip(elemValue.getValue());
    }

    /**
     * Helper to extract a list of strings from plugin configuration element, that requires an
     * array of strings.
     * 
     * @param dom      the parent {@link Xpp3Dom} element. If {@code null} it will use
     *                 the {@literal configuration} DOM.
     * @param parentElemName the parent element name or the element name encapsulating the 
     *                       list parameters.
     * @param childElemName  the name of the child element.
     * @return               A list of {@code string} values or {@code empty list} if no child element
     *                       exist.
     */
    public List<String> childValues(
            final Xpp3Dom dom, final String parentElemName, final String childElemName) {
      final List<String> values = new LinkedList<String>();
      final Xpp3Dom cfgDom = dom == null ? this.configuration : dom;
      final Xpp3Dom parentDom = cfgDom.getChild(parentElemName);      
      if (parentDom != null) {
        final Xpp3Dom[] childDom = parentDom.getChildren(childElemName);
        if (childDom != null) {
          for (Xpp3Dom elemDom : childDom) {
            final String value = elemDom.getValue();
            if (value != null) {
              values.add(value);
            }
          }
        }
      }
      return values;
    }

    public List<String> splitValueAsList(
            final Xpp3Dom dom, final String elemName, final String separator) {
        final String value = this.value(dom, elemName);
        
        if (value == null) {
            return ImmutableList.of();
        }
        return ImmutableList.of(StringUtils.split(value, separator));
    }
    
    public boolean shouldDisableConfigurator() {
        final String value = this.value(this.m2eConfig, "disable");
        //if not configured then we assume we want to run project configurator
        if (value == null) {
            return true;
        }
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Factory to create new instances of {@link MavenPluginConfigurationExtractor}.
     * 
     * @param mavenPluginWrapper The {@link MavenPluginWrapper} instance.
     * @return                   A new instance of {@link MavenPluginConfigurationExtractor}.
     */
    public static MavenPluginConfigurationExtractor newInstance(
            final MavenPluginWrapper mavenPluginWrapper) {
        final Xpp3Dom pluginConfigDom = mavenPluginWrapper.getPluginConfigurationDom();
        Xpp3Dom m2eConfig = pluginConfigDom.getChild(M2ECLIPSE_CONFIG_ELEM_NAME);
        if (m2eConfig == null) {
            m2eConfig = new Xpp3Dom(M2ECLIPSE_CONFIG_ELEM_NAME);
        }
        return new MavenPluginConfigurationExtractor(new Xpp3Dom(pluginConfigDom), m2eConfig);
    }

    public static MavenPluginConfigurationExtractor newInstance(
            final String pluginGroupId,
            final String pluginArtifactId,
            final MavenProject mavenProject) {
        final MavenPluginWrapper projectMavenPlugin = MavenPluginWrapper
            .newInstance(pluginGroupId, pluginArtifactId, mavenProject);
        return MavenPluginConfigurationExtractor.newInstance(projectMavenPlugin);
    }
}
