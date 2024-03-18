package com.github.davidcarboni.restolino;

import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Determines the correct configuration, based on environment variables, system
 * properties and checking the classloader hierarchy for a classes URL.
 *
 * @author david
 */
public class Configuration {

    private static final Logger log = getLogger(Configuration.class);
    public static final String PORT = "PORT";
    public static final String JETTY_MAX_THREADS = "JETTY_MAX_THREADS";
    public static final String CLASSES = "restolino.classes";
    public static final String PACKAGE_PREFIX = "restolino.packageprefix";
    public static final String FILES = "restolino.files";
    public static final String FILES_RESOURCE = "web";
    public static final String AUTH_USERNAME = "restolino.username";
    public static final String AUTH_PASSWORD = "restolino.password";
    public static final String AUTH_REALM = "restolino.realm";

    public static final String JETTY_REQUEST_HEADER_SIZE = "JETTY_REQUEST_HEADER_SIZE";

    /**
     * The Jetty server port.
     */
    public int port = 8080;

    /**
     * The Jetty server max threads.
     */
    public int maxThreads = 200;

    /**
     * If files will be dynamically reloaded, true.
     */
    public boolean filesReloadable;

    /**
     * If files will be dynamically reloaded, the URL from which they will be
     * loaded. If not reloading, this will typically be URL that points to a
     * <code>files/...</code> resource directory in your JAR.
     */
    public URL filesUrl;

    /**
     * If classes will be dynamically reloaded, true.
     */
    public boolean classesReloadable;

    /**
     * If a <code>.../classes</code> entry is present on the classpath, that URL
     * from the classloader hierarchy. This is designed to prevent uncertainty
     * and frustration if you have correctly configured class reloading, but
     * have also accidentally includedd your classes on the classpath. This
     * would leand to code not being reloaded and possibly even confusing error
     * messages because the classes on the classpath will take precedence
     * (because class loaders delegate upwards).
     */
    public URL classesInClasspath;

    /**
     * If classes will be dynamically reloaded, a file URL for the path which
     * will be monitored for changes in order to trigger reloading.
     */
    public URL classesUrl;

    /**
     * If classes will be dynamically reloaded, the package prefix to scan. This
     * is optional but, if set, it avoids scanning all classes in all
     * dependencies, making reloads faster. This is passed directly to
     * {@link Reflections}.
     */
    public String packagePrefix;

    /**
     * Whether authentication has been enabled, by setting at least
     * {@value #AUTH_USERNAME}.
     */
    public boolean authenticationEnabled;

    /**
     * If set, http basic authentication will be enabled and this will be the
     * username. ({@value #AUTH_USERNAME})
     */
    public String username;

    /**
     * If http basic authentication is enabled (by setting
     * {@value #AUTH_USERNAME}) this will be used as the password. (
     * {@value #AUTH_PASSWORD})
     */
    public String password;

    /**
     * Optional. If http basic authentication is enabled (by setting
     * {@value #AUTH_USERNAME}) this will be the "realm". ({@value #AUTH_REALM})
     *
     * @see <a href=
     * "http://stackoverflow.com/questions/10892336/realm-name-in-tomcat-web-xml"
     * >http://stackoverflow.com/questions/10892336/realm-name-in-tomcat-web-xml</a>
     */
    public String realm;

    /**
     * The Jetty server request header size.
     */
    public int jettyRequestHeaderSize = 8192;

    @Override
    public String toString() {

        StringBuilder result = new StringBuilder();

        // Parameters:
        result.append("\nEnvironment/property values:");
        result.append("\n * " + PORT + "=" + getValue(PORT));
        result.append("\n * " + FILES + "=" + getValue(FILES));
        result.append("\n * " + CLASSES + "=" + getValue(CLASSES));

        // Resolved configuration:
        result.append("\nResolved configuration:");
        result.append("\n - port:\t" + port);
        result.append("\n - filesReloadable:\t" + filesReloadable);
        result.append("\n - filesUrl:\t" + filesUrl);
        result.append("\n - classesReloadable:\t" + classesReloadable);
        result.append("\n - classesInClasspath:\t" + classesInClasspath);
        result.append("\n - classesUrl:\t" + classesUrl);
        result.append("\n - packagePrefix:\t" + packagePrefix);
        result.append("\n - jettyRequestHeaderSize:\t" + jettyRequestHeaderSize);

        // Basic authentication
        result.append("\nBasic Auth:");
        if (authenticationEnabled) {
            result.append("\n - username:\t" + username);
            result.append("\n - password:\t" + (StringUtils.isNotBlank(password) ? "(yes)" : "(no)"));
            result.append("\n - realm:\t" + realm);
        } else {
            result.append("\n - not configured. To enable, set " + AUTH_USERNAME + ", " + AUTH_PASSWORD + " and (optionally) " + AUTH_REALM);
        }

        return result.toString();
    }

    public Configuration() {

        // The server port and maxThreads:
        String port = getValue(PORT);
        String maxThreads = getValue(JETTY_MAX_THREADS);

        // The reloadable parameters:
        String files = getValue(FILES);
        String classes = getValue(CLASSES);

        // Authentication parameters:
        String username = getValue(AUTH_USERNAME);
        String password = getValue(AUTH_PASSWORD);
        String realm = getValue(AUTH_REALM);

        // server request header size:
        System.setProperty(JETTY_REQUEST_HEADER_SIZE, StringUtils.EMPTY);
        String requestHeaderSize = getValue(JETTY_REQUEST_HEADER_SIZE);

        // Set up the configuration:
        configurePort(port);
        configureMaxThreads(maxThreads);
        configureFiles(files);
        configureClasses(classes);
        configureAuthentication(username, password, realm);
        configureJettyRequestHeaderSize(requestHeaderSize);
    }

    /**
      * Configures the server request header size,
      * default is 8192 bytes.
      *
      * @param requestHeaderSize The value of the {@value #JETTY_REQUEST_HEADER_SIZE} parameter.
      */
    void configureJettyRequestHeaderSize(String requestHeaderSize) {
        try {
            this.jettyRequestHeaderSize = Integer.parseInt(requestHeaderSize);
            log.info("Using jettyRequestHeaderSize {}", this.jettyRequestHeaderSize);
        } catch (NumberFormatException e) {
            log.info("Unable to parse server JETTY_REQUEST_HEADER_SIZE variable ({}). Defaulting to jettyRequestHeaderSize {}", requestHeaderSize, this.jettyRequestHeaderSize);
        }
    }

    /**
     * Configures the server port by attempting to parse the given parameter,
     * but failing gracefully if that doesn't work out.
     *
     * @param port The value of the {@value #PORT} parameter.
     */
    void configurePort(String port) {

        if (StringUtils.isNotBlank(port)) {
            try {
                this.port = Integer.parseInt(port);
                log.info("Using port {}", this.port);
            } catch (NumberFormatException e) {
                log.info("Unable to parse server PORT variable ({}). Defaulting to port {}", port, this.port);
            }
        }
    }

    /**
     * Configures the server max threads by attempting to parse the given parameter,
     * but failing gracefully if that doesn't work out.
     *
     * @param maxThreads The value of the {@value #JETTY_MAX_THREADS} parameter.
     */
    void configureMaxThreads(String maxThreads) {

        if (StringUtils.isNotBlank(maxThreads)) {
            try {
                this.maxThreads = Integer.parseInt(maxThreads);
                log.info("Using maxThreads {}", this.maxThreads);
            } catch (NumberFormatException e) {
                log.info("Unable to parse server JETTY_MAX_THREADS variable ({}). Defaulting to maxThreads {}", maxThreads, this.maxThreads);
            }
        }
    }

    /**
     * Sets up configuration for serving static files (if any).
     *
     * @param path The directory that contains static files.
     */
    void configureFiles(String path) {

        // If the property is set, reload from a local directory:
        if (StringUtils.isNotBlank(path)) {
            configureFilesReloadable(path);
        } else {
            configureFilesResource();
        }
        filesReloadable = filesUrl != null;

        // Communicate:
        showFilesConfiguration();
    }

    /**
     * Sets up configuration for reloading classes.
     *
     * @param path The directory that contains compiled classes. This will be
     *             monitored for changes.
     */
    void configureClasses(String path) {

        findClassesInClasspath();

        if (StringUtils.isNotBlank(path)) {
            // If the path is set, set up class reloading:
            configureClassesReloadable(path);
        }
        packagePrefix = getValue(PACKAGE_PREFIX);
        classesReloadable = classesUrl != null && classesInClasspath == null;

        // Communicate:
        showClassesConfiguration();
    }

    /**
     * Sets up authentication.
     *
     * @param username The HTTP basic authentication username.
     * @param password The HTTP basic authentication password.
     * @param realm    Optional. Defaults to "restolino".
     */
    private void configureAuthentication(String username, String password, String realm) {

        // If the username is set, set up authentication:
        if (StringUtils.isNotBlank(username)) {

            this.username = username;
            this.password = password;
            this.realm = StringUtils.defaultIfBlank(realm, "restolino");

            authenticationEnabled = true;
        }
    }

    /**
     * Configures static file serving from a directory. This will be reloadable,
     * so is most useful for development (rather than deployment). This
     * typically serves files from the <code>src/main/resources/files/...</code>
     * directory of your development project.
     * <p>
     * NB This provides an efficient development workflow, allowing you to see
     * static file changes without having to rebuild.
     */
    void configureFilesReloadable(String path) {

        try {
            // Running with reloading:
            Path filesPath = FileSystems.getDefault().getPath(path);
            filesUrl = filesPath.toUri().toURL();
        } catch (IOException e) {
            throw new RuntimeException("Error determining files path/url for: " + path, e);
        }
    }

    /**
     * Configures static file serving from the classpath. This will not be
     * reloadable, so is most useful for deployment (rather than development).
     * This typically serves files from the <code>web/...</code> directory at
     * the root of a <code>*-jar-with-dependencies.jar</code> artifact.
     * <p>
     * NB Part of the intent here is to support a compact and simple deployment
     * model (single JAR) that discourages changes in the target environment
     * (because the JAR is not unpacked) and favours automated deployment of a
     * new version (or rollback to a previous version) as the way to make
     * changes.
     */
    void configureFilesResource() {

        // Check for a resource on the classpath (when deployed):
        ClassLoader classLoader = Configuration.class.getClassLoader();
        filesUrl = classLoader.getResource(FILES_RESOURCE);
    }

    /**
     * Scans the {@link ClassLoader} hierarchy to check if there is a
     * <code>.../classes</code> entry present. This is designed to prevent
     * uncertainty and frustration if you have correctly configured class
     * reloading, but have also accidentally included your classes on the
     * classpath. This would lead to code not being reloaded and possibly even
     * confusing error messages because the classes on the classpath will take
     * precedence over reloaded classes (because class loaders normally delegate
     * upwards).
     */
    void findClassesInClasspath() {

        ClassLoader classLoader = Configuration.class.getClassLoader();
        do {
            if (URLClassLoader.class.isAssignableFrom(classLoader.getClass())) {
                URLClassLoader urlClassLoader = (URLClassLoader) classLoader;

                findClassesInClassloader(urlClassLoader);
                if (classesInClasspath != null) {
                    break;
                }
            }

            // Check the parent:
            classLoader = classLoader.getParent();

        } while (classLoader != null);
    }

    /**
     * Scans the {@link ClassLoader} hierarchy to check if there is a
     * <code>.../classes</code> entry present. This is designed to prevent
     * uncertainty and frustration if you have correctly configured class
     * reloading, but have also accidentally included your classes on the
     * classpath. This would lead to code not being reloaded and possibly even
     * confusing error messages because the classes on the classpath will take
     * precedence over reloaded classes (because class loaders normally delegate
     * upwards).
     */
    void findClassesInClassloader(URLClassLoader urlClassLoader) {

        // Check for a "classes" URL:
        for (URL url : urlClassLoader.getURLs()) {
            String urlPath = StringUtils.lowerCase(url.getPath());
            if (StringUtils.endsWithAny(urlPath, "/classes", "/classes/")) {
                classesInClasspath = url;
            }
        }
    }

    /**
     * Configures dynamic class reloading. This is most useful for development
     * (rather than deployment). This typically reloads classes from the
     * <code>target/classes/...</code> directory of your development project.
     * <p/>
     * NB This provides an efficient development workflow, allowing you to see
     * code changes without having to redeploy. It also supports stateless
     * webapp design because the entire classes classloader is replaced every
     * time there is a change (so you'll lose stuff like static variable
     * values).
     */
    void configureClassesReloadable(String path) {

        try {
            // Set up reloading:
            Path classesPath = FileSystems.getDefault().getPath(path);
            classesUrl = classesPath.toUri().toURL();
        } catch (IOException e) {
            throw new RuntimeException("Error starting class reloader", e);
        }
    }

    /**
     * Prints out a message confirming the static file serving configuration.
     */
    void showFilesConfiguration() {

        // Message to communicate the resolved configuration:
        String message;
        if (filesUrl != null) {
            String reload = filesReloadable ? "reloadable" : "non-reloadable";
            message = "Files will be served from: " + filesUrl + " (" + reload + ")";
        } else {
            message = "No static files will be served.";
        }
        log.info("Files: {}", message);
    }

    /**
     * Prints out a message confirming the class reloading configuration.
     */
    void showClassesConfiguration() {

        // Warning about a classes folder present in the classpath:
        if (classesInClasspath != null) {
            log.warn("Dynamic class reloading is disabled because a classes URL is present in the classpath. P"
                    + "lease launch without including your classes directory: {}", classesInClasspath);
        }

        // Message to communicate the resolved configuration:
        String message;
        if (classesReloadable) {
            if (StringUtils.isNotBlank(packagePrefix)) {
                message = "Classes will be reloaded from: " + classesUrl;
            } else {
                message = "Classes will be reloaded from package " + packagePrefix + " at: " + classesUrl;
            }
        } else {
            message = "Classes will not be dynamically reloaded.";
        }
        log.info("Classes: {}", message);
    }

    /**
     * Gets a configured value for the given key from either the system
     * properties or an environment variable.
     *
     * @param key The name of the configuration value.
     * @return The system property corresponding to the given key (e.g.
     * -Dkey=value). If that is blank, the environment variable
     * corresponding to the given key (e.g. EXPORT key=value). If that
     * is blank, {@link StringUtils#EMPTY}.
     */
    static String getValue(String key) {
        String result = StringUtils.defaultIfBlank(System.getProperty(key), StringUtils.EMPTY);
        result = StringUtils.defaultIfBlank(result, System.getenv(key));
        return result;
    }

}
