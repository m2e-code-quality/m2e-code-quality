package org.maven.ide.eclipse.extensions.shared.util;

/**
 * A convenient wrapper around {@link RuntimeException} thrown when any
 * time a configuration fails.
 * 
 */
public class ConfigurationException
        extends RuntimeException {

    private static final long serialVersionUID = 685082554934520727L;

    public ConfigurationException(final String message) {
        super(message);
    }
    
    public ConfigurationException(final Throwable cause) {
        super(cause);
    }

    public ConfigurationException(final String message, final Throwable cause) {
        super(message, cause);
    }

}
