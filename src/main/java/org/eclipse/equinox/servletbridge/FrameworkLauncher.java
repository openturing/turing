/*******************************************************************************
 * Copyright (c) 2005, 2010 Cognos Incorporated, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Cognos Incorporated - initial API and implementation
 *     IBM Corporation - bug fixes and enhancements
 *     Code 9 - bug fixes and enhancements
 *******************************************************************************/
package org.eclipse.equinox.servletbridge;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

/**
 * The FrameworkLauncher provides the logic to:
 * 1) init
 * 2) deploy
 * 3) start
 * 4) stop
 * 5) undeploy
 * 6) destroy
 * an instance of the OSGi framework. 
 * These 6 methods are provided to help manage the life-cycle and are called from outside this
 * class by the BridgeServlet. To create an extended FrameworkLauncher over-ride these methods to allow
 * custom behavior.  
 */
public class FrameworkLauncher {

	private static final String REFERENCE_SCHEME = "reference:"; //$NON-NLS-1$
	private static final String CONFIG_INI = "config.ini"; //$NON-NLS-1$
	private static final String DOT_JAR = ".jar"; //$NON-NLS-1$
	private static final String WS_DELIM = " \t\n\r\f"; //$NON-NLS-1$
	protected static final String FILE_SCHEME = "file:"; //$NON-NLS-1$
	protected static final String FRAMEWORK_BUNDLE_NAME = "org.eclipse.osgi"; //$NON-NLS-1$
	protected static final String STARTER = "org.eclipse.core.runtime.adaptor.EclipseStarter"; //$NON-NLS-1$
	protected static final String FRAMEWORKPROPERTIES = "org.eclipse.osgi.framework.internal.core.FrameworkProperties"; //$NON-NLS-1$
	protected static final String NULL_IDENTIFIER = "@null"; //$NON-NLS-1$
	protected static final String OSGI_FRAMEWORK = "osgi.framework"; //$NON-NLS-1$
	protected static final String OSGI_FRAMEWORK_EXTENSIONS = "osgi.framework.extensions"; //$NON-NLS-1$
	protected static final String OSGI_INSTANCE_AREA = "osgi.instance.area"; //$NON-NLS-1$
	protected static final String OSGI_CONFIGURATION_AREA = "osgi.configuration.area"; //$NON-NLS-1$
	protected static final String OSGI_INSTALL_AREA = "osgi.install.area"; //$NON-NLS-1$
	protected static final String OSGI_FORCED_RESTART = "osgi.forcedRestart"; //$NON-NLS-1$
	protected static final String RESOURCE_BASE = "/WEB-INF/"; //$NON-NLS-1$
	protected static final String ECLIPSE = "eclipse/"; //$NON-NLS-1$
	protected static final String LAUNCH_INI = "launch.ini"; //$NON-NLS-1$

	private static final String EXTENSIONBUNDLE_DEFAULT_BSN = "org.eclipse.equinox.servletbridge.extensionbundle"; //$NON-NLS-1$
	private static final String EXTENSIONBUNDLE_DEFAULT_VERSION = "1.2.0"; //$NON-NLS-1$
	private static final String MANIFEST_VERSION = "Manifest-Version"; //$NON-NLS-1$
	private static final String BUNDLE_MANIFEST_VERSION = "Bundle-ManifestVersion"; //$NON-NLS-1$
	private static final String BUNDLE_NAME = "Bundle-Name"; //$NON-NLS-1$
	private static final String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName"; //$NON-NLS-1$
	private static final String BUNDLE_VERSION = "Bundle-Version"; //$NON-NLS-1$
	private static final String FRAGMENT_HOST = "Fragment-Host"; //$NON-NLS-1$
	private static final String EXPORT_PACKAGE = "Export-Package"; //$NON-NLS-1$

	private static final String CONFIG_COMMANDLINE = "commandline"; //$NON-NLS-1$
	private static final String CONFIG_EXTENDED_FRAMEWORK_EXPORTS = "extendedFrameworkExports"; //$NON-NLS-1$
	private static final String CONFIG_OVERRIDE_AND_REPLACE_EXTENSION_BUNDLE = "overrideAndReplaceExtensionBundle"; //$NON-NLS-1$

	static final PermissionCollection allPermissions = new PermissionCollection() {
		private static final long serialVersionUID = 482874725021998286L;
		// The AllPermission permission
		Permission allPermission = new AllPermission();

		// A simple PermissionCollection that only has AllPermission
		public void add(Permission permission) {
			// do nothing
		}

		public boolean implies(Permission permission) {
			return true;
		}

		public Enumeration elements() {
			return new Enumeration() {
				int cur = 0;

				public boolean hasMoreElements() {
					return cur < 1;
				}

				public Object nextElement() {
					if (cur == 0) {
						cur = 1;
						return allPermission;
					}
					throw new NoSuchElementException();
				}
			};
		}
	};

	static {
		// We do this to ensure the anonymous Enumeration class in allPermissions is pre-loaded 
		if (allPermissions.elements() == null)
			throw new IllegalStateException();
	}

	protected ServletConfig config;
	protected ServletContext context;
	protected String resourceBase;
	private File platformDirectory;
	private ClassLoader frameworkContextClassLoader;
	private CloseableURLClassLoader frameworkClassLoader;

	void init(ServletConfig servletConfig) {
		config = servletConfig;
		context = servletConfig.getServletContext();
		initResourceBase();
		init();
	}

	/**
	 * try to find the resource base for this webapp by looking for the launcher initialization file.
	 */
	protected void initResourceBase() {
		try {
			if (context.getResource(RESOURCE_BASE + LAUNCH_INI) != null) {
				resourceBase = RESOURCE_BASE;
				return;
			}
			if (context.getResource(RESOURCE_BASE + ECLIPSE + LAUNCH_INI) != null) {
				resourceBase = RESOURCE_BASE + ECLIPSE;
				return;
			}
		} catch (MalformedURLException e) {
			// ignore
		}
		// If things don't work out, use the default resource base
		resourceBase = RESOURCE_BASE;
	}

	/**
	 * init is the first method called on the FrameworkLauncher and can be used for any initial setup.
	 * The default behavior is to do nothing.
	 */
	public void init() {
		// do nothing for now
	}

	/**
	 * destroy is the last method called on the FrameworkLauncher and can be used for any final cleanup.
	 * The default behavior is to do nothing.
	 */
	public void destroy() {
		// do nothing for now
	}

	/**
	 * deploy is used to move the OSGi framework libraries into a location suitable for execution.
	 * The default behavior is to copy the contents of the webapp's WEB-INF/eclipse directory
	 * to the webapp's temp directory.
	 */
	public synchronized void deploy() {
		if (platformDirectory != null) {
			context.log("Framework is already deployed"); //$NON-NLS-1$
			return;
		}

		File servletTemp = (File) context.getAttribute("javax.servlet.context.tempdir"); //$NON-NLS-1$
		platformDirectory = new File(servletTemp, "eclipse"); //$NON-NLS-1$
		if (!platformDirectory.exists()) {
			platformDirectory.mkdirs();
		}

		copyResource(resourceBase + "configuration/", new File(platformDirectory, "configuration")); //$NON-NLS-1$ //$NON-NLS-2$
		copyResource(resourceBase + "features/", new File(platformDirectory, "features")); //$NON-NLS-1$ //$NON-NLS-2$
		File plugins = new File(platformDirectory, "plugins"); //$NON-NLS-1$
		copyResource(resourceBase + "plugins/", plugins); //$NON-NLS-1$
		copyResource(resourceBase + "p2/", new File(platformDirectory, "p2")); //$NON-NLS-1$ //$NON-NLS-2$
		deployExtensionBundle(plugins);
		copyResource(resourceBase + ".eclipseproduct", new File(platformDirectory, ".eclipseproduct")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * deployExtensionBundle will generate the Servletbridge extensionbundle if it is not already present in the platform's
	 * plugin directory. By default it exports "org.eclipse.equinox.servletbridge" and a versioned export of the Servlet API.
	 * Additional exports can be added by using the "extendedFrameworkExports" initial-param in the ServletConfig
	 */
	private void deployExtensionBundle(File plugins) {
		// we might want to parameterize the extension bundle BSN in the future
		final String extensionBundleBSN = EXTENSIONBUNDLE_DEFAULT_BSN;
		File extensionBundleFile = findExtensionBundleFile(plugins, extensionBundleBSN);

		if (extensionBundleFile == null)
			generateExtensionBundle(plugins, extensionBundleBSN, EXTENSIONBUNDLE_DEFAULT_VERSION);
		else if (Boolean.valueOf(config.getInitParameter(CONFIG_OVERRIDE_AND_REPLACE_EXTENSION_BUNDLE)).booleanValue()) {
			String extensionBundleVersion = findExtensionBundleVersion(extensionBundleFile, extensionBundleBSN);
			if (extensionBundleFile.isDirectory()) {
				deleteDirectory(extensionBundleFile);
			} else {
				extensionBundleFile.delete();
			}
			generateExtensionBundle(plugins, extensionBundleBSN, extensionBundleVersion);
		} else {
			processExtensionBundle(extensionBundleFile);
		}
	}

	private File findExtensionBundleFile(File plugins, final String extensionBundleBSN) {
		FileFilter extensionBundleFilter = new FileFilter() {
			public boolean accept(File candidate) {
				return candidate.getName().startsWith(extensionBundleBSN + "_"); //$NON-NLS-1$
			}
		};
		File[] extensionBundles = plugins.listFiles(extensionBundleFilter);
		if (extensionBundles.length == 0)
			return null;

		if (extensionBundles.length > 1) {
			for (int i = 1; i < extensionBundles.length; i++) {
				if (extensionBundles[i].isDirectory()) {
					deleteDirectory(extensionBundles[i]);
				} else {
					extensionBundles[i].delete();
				}
			}
		}
		return extensionBundles[0];
	}

	private String findExtensionBundleVersion(File extensionBundleFile, String extensionBundleBSN) {
		String fileName = extensionBundleFile.getName();
		if (fileName.endsWith(DOT_JAR)) {
			return fileName.substring(extensionBundleBSN.length() + 1, fileName.length() - DOT_JAR.length());
		}
		return fileName.substring(extensionBundleBSN.length() + 1);
	}

	private void generateExtensionBundle(File plugins, String extensionBundleBSN, String extensionBundleVersion) {
		Manifest mf = new Manifest();
		Attributes attribs = mf.getMainAttributes();
		attribs.putValue(MANIFEST_VERSION, "1.0"); //$NON-NLS-1$
		attribs.putValue(BUNDLE_MANIFEST_VERSION, "2"); //$NON-NLS-1$
		attribs.putValue(BUNDLE_NAME, "Servletbridge Extension Bundle"); //$NON-NLS-1$
		attribs.putValue(BUNDLE_SYMBOLIC_NAME, extensionBundleBSN);
		attribs.putValue(BUNDLE_VERSION, extensionBundleVersion);
		attribs.putValue(FRAGMENT_HOST, "system.bundle; extension:=framework"); //$NON-NLS-1$

		String servletVersion = context.getMajorVersion() + "." + context.getMinorVersion(); //$NON-NLS-1$
		String packageExports = "org.eclipse.equinox.servletbridge; version=1.1" + //$NON-NLS-1$
				", javax.servlet; version=" + servletVersion + //$NON-NLS-1$
				", javax.servlet.http; version=" + servletVersion + //$NON-NLS-1$
				", javax.servlet.resources; version=" + servletVersion; //$NON-NLS-1$

		String extendedExports = config.getInitParameter(CONFIG_EXTENDED_FRAMEWORK_EXPORTS);
		if (extendedExports != null && extendedExports.trim().length() != 0)
			packageExports += ", " + extendedExports; //$NON-NLS-1$

		attribs.putValue(EXPORT_PACKAGE, packageExports);
		writeJarFile(new File(plugins, extensionBundleBSN + "_" + extensionBundleVersion + DOT_JAR), mf); //$NON-NLS-1$
	}

	private void processExtensionBundle(File extensionBundleFile) {
		String fileName = extensionBundleFile.getName();
		if (fileName.endsWith(DOT_JAR)) {
			Manifest mf = readJarFile(extensionBundleFile);
			if (mf == null)
				return;
			Attributes attributes = mf.getMainAttributes();
			String exportPackage = (String) attributes.remove(new Attributes.Name("X-Deploy-Export-Package")); //$NON-NLS-1$
			if (exportPackage != null) {
				attributes.putValue("Export-Package", exportPackage); //$NON-NLS-1$
				writeJarFile(extensionBundleFile, mf);
			}
		}
	}

	private void writeJarFile(File jarFile, Manifest mf) {
		try {
			JarOutputStream jos = null;
			try {
				jos = new JarOutputStream(new FileOutputStream(jarFile), mf);
				jos.finish();
			} finally {
				if (jos != null)
					jos.close();
			}
		} catch (IOException e) {
			context.log("Error writing extension bundle", e); //$NON-NLS-1$
		}
	}

	private Manifest readJarFile(File jarFile) {
		try {
			JarInputStream jis = null;
			try {
				jis = new JarInputStream(new FileInputStream(jarFile));
				return jis.getManifest();
			} finally {
				if (jis != null)
					jis.close();
			}
		} catch (IOException e) {
			context.log("Error reading extension bundle", e); //$NON-NLS-1$
		}
		return null;
	}

	/** undeploy is the reverse operation of deploy and removes the OSGi framework libraries from their
	 * execution location. Typically this method will only be called if a manual undeploy is requested in the 
	 * ServletBridge.
	 * By default, this method removes the OSGi install and also removes the workspace.
	 */
	public synchronized void undeploy() {
		if (platformDirectory == null) {
			context.log("Undeploy unnecessary. - (not deployed)"); //$NON-NLS-1$
			return;
		}

		if (frameworkClassLoader != null) {
			throw new IllegalStateException("Could not undeploy Framework - (not stopped)"); //$NON-NLS-1$
		}

		deleteDirectory(new File(platformDirectory, "configuration")); //$NON-NLS-1$
		deleteDirectory(new File(platformDirectory, "features")); //$NON-NLS-1$
		deleteDirectory(new File(platformDirectory, "plugins")); //$NON-NLS-1$
		deleteDirectory(new File(platformDirectory, "workspace")); //$NON-NLS-1$
		deleteDirectory(new File(platformDirectory, "p2")); //$NON-NLS-1$

		new File(platformDirectory, ".eclipseproduct").delete(); //$NON-NLS-1$
		platformDirectory = null;
	}

	/** start is used to "start" a previously deployed OSGi framework
	 * The default behavior will read launcher.ini to create a set of initial properties and
	 * use the "commandline" configuration parameter to create the equivalent command line arguments
	 * available when starting Eclipse. 
	 */
	public synchronized void start() {
		if (platformDirectory == null)
			throw new IllegalStateException("Could not start the Framework - (not deployed)"); //$NON-NLS-1$

		if (frameworkClassLoader != null) {
			context.log("Framework is already started"); //$NON-NLS-1$
			return;
		}

		Map initialPropertyMap = buildInitialPropertyMap();
		String[] args = buildCommandLineArguments();

		// Handle commandline -D properties
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.startsWith("-D")) { //$NON-NLS-1$
				int equalsIndex = arg.indexOf('=');
				if (equalsIndex == -1) {
					initialPropertyMap.put(arg.substring(2), ""); //$NON-NLS-1$
				} else {
					String key = arg.substring(2, equalsIndex);
					String value = arg.substring(equalsIndex + 1);
					if (value.startsWith("\"") && value.endsWith("\"")) //$NON-NLS-1$//$NON-NLS-2$
						value = value.substring(1, value.length() - 1);
					setInitialProperty(initialPropertyMap, key, value);
				}
			}
		}

		ClassLoader original = Thread.currentThread().getContextClassLoader();
		try {
			System.setProperty("osgi.framework.useSystemProperties", "false"); //$NON-NLS-1$ //$NON-NLS-2$

			URL[] frameworkURLs = findFrameworkURLs(initialPropertyMap);
			frameworkClassLoader = new ChildFirstURLClassLoader(frameworkURLs, this.getClass().getClassLoader());
			Class clazz = frameworkClassLoader.loadClass(STARTER);

			Method setInitialProperties = clazz.getMethod("setInitialProperties", new Class[] {Map.class}); //$NON-NLS-1$
			setInitialProperties.invoke(null, new Object[] {initialPropertyMap});

			registerRestartHandler(clazz);

			Method runMethod = clazz.getMethod("startup", new Class[] {String[].class, Runnable.class}); //$NON-NLS-1$
			runMethod.invoke(null, new Object[] {args, null});

			frameworkContextClassLoader = Thread.currentThread().getContextClassLoader();
		} catch (InvocationTargetException ite) {
			Throwable t = ite.getTargetException();
			if (t == null)
				t = ite;
			context.log("Error while starting Framework", t); //$NON-NLS-1$
			throw new RuntimeException(t.getMessage());
		} catch (Exception e) {
			context.log("Error while starting Framework", e); //$NON-NLS-1$
			throw new RuntimeException(e.getMessage());
		} finally {
			Thread.currentThread().setContextClassLoader(original);
		}
	}

	private URL[] findFrameworkURLs(Map initialPropertyMap) {
		List frameworkURLs = new ArrayList();
		String installArea = (String) initialPropertyMap.get(OSGI_INSTALL_AREA);
		if (installArea.startsWith(FILE_SCHEME)) {
			installArea = installArea.substring(FILE_SCHEME.length());
		}
		File installBase = new File(installArea);

		// OSGi framework
		String osgiFramework = (String) initialPropertyMap.get(OSGI_FRAMEWORK);
		File osgiFrameworkFile = null;
		if (osgiFramework == null) {
			// search for osgi.framework in osgi.install.area
			String path = new File(installBase, "plugins").toString(); //$NON-NLS-1$
			path = searchFor(FRAMEWORK_BUNDLE_NAME, path);
			if (path == null)
				throw new RuntimeException("Could not find framework"); //$NON-NLS-1$

			osgiFrameworkFile = new File(path);
		} else {
			if (osgiFramework.startsWith(FILE_SCHEME)) {
				osgiFramework = osgiFramework.substring(FILE_SCHEME.length());
			}
			osgiFrameworkFile = new File(osgiFramework);
			if (!osgiFrameworkFile.isAbsolute())
				osgiFrameworkFile = new File(installBase, osgiFramework);
		}

		try {
			URL frameworkURL = osgiFrameworkFile.toURL();
			frameworkURLs.add(frameworkURL);
			// ensure the framework URL is absolute
			initialPropertyMap.put(OSGI_FRAMEWORK, frameworkURL.toExternalForm());
		} catch (MalformedURLException e) {
			throw new RuntimeException("Could not find framework -- " + e.getMessage()); //$NON-NLS-1$
		}

		// OSGi framework extensions
		String osgiFrameworkExtensions = (String) initialPropertyMap.get(OSGI_FRAMEWORK_EXTENSIONS);
		if (osgiFrameworkExtensions != null) {
			StringTokenizer tokenizer = new StringTokenizer(osgiFrameworkExtensions, ",");
			while (tokenizer.hasMoreTokens()) {
				String extension = tokenizer.nextToken().trim();
				if (extension.length() == 0)
					continue;

				URL extensionURL = findExtensionURL(extension, osgiFrameworkFile);
				if (extensionURL != null) {
					frameworkURLs.add(extensionURL);
				}
			}
		}
		return (URL[]) frameworkURLs.toArray(new URL[frameworkURLs.size()]);
	}

	private URL findExtensionURL(String extension, File osgiFrameworkFile) {
		File extensionFile = null;
		if (extension.startsWith(REFERENCE_SCHEME)) {
			extension = extension.substring(REFERENCE_SCHEME.length());
			if (!extension.startsWith(FILE_SCHEME))
				throw new RuntimeException("Non-file scheme for framework extension URL -- " + extension); //$NON-NLS-1$
			extension = extension.substring(FILE_SCHEME.length());
			extensionFile = new File(extension);
			if (!extensionFile.isAbsolute())
				extensionFile = new File(osgiFrameworkFile.getParentFile(), extension);
		} else {
			String fullExtensionPath = searchFor(extension, osgiFrameworkFile.getParent());
			if (fullExtensionPath == null)
				return null;
			extensionFile = new File(fullExtensionPath);
		}

		try {
			return extensionFile.toURL();
		} catch (MalformedURLException e) {
			throw new RuntimeException("Could not find framework extension -- " + extensionFile.getAbsolutePath() + " : " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private void registerRestartHandler(Class starterClazz) throws NoSuchMethodException, ClassNotFoundException, IllegalAccessException, InvocationTargetException {
		Method registerFrameworkShutdownHandler = null;
		try {
			registerFrameworkShutdownHandler = starterClazz.getDeclaredMethod("internalAddFrameworkShutdownHandler", new Class[] {Runnable.class}); //$NON-NLS-1$
		} catch (NoSuchMethodException e) {
			// Ok. However we will not support restart events. Log this as info
			context.log(starterClazz.getName() + " does not support setting a shutdown handler. Restart handling is disabled."); //$NON-NLS-1$
			return;
		}
		if (!registerFrameworkShutdownHandler.isAccessible())
			registerFrameworkShutdownHandler.setAccessible(true);
		Runnable restartHandler = createRestartHandler();
		registerFrameworkShutdownHandler.invoke(null, new Object[] {restartHandler});
	}

	private Runnable createRestartHandler() throws ClassNotFoundException, NoSuchMethodException {
		Class frameworkPropertiesClazz = frameworkClassLoader.loadClass(FRAMEWORKPROPERTIES);
		final Method getProperty = frameworkPropertiesClazz.getMethod("getProperty", new Class[] {String.class}); //$NON-NLS-1$
		Runnable restartHandler = new Runnable() {
			public void run() {
				try {
					String forcedRestart = (String) getProperty.invoke(null, new Object[] {OSGI_FORCED_RESTART});
					if (Boolean.valueOf(forcedRestart).booleanValue()) {
						stop();
						start();
					}
				} catch (InvocationTargetException ite) {
					Throwable t = ite.getTargetException();
					if (t == null)
						t = ite;
					throw new RuntimeException(t.getMessage());
				} catch (Exception e) {
					throw new RuntimeException(e.getMessage());
				}
			}
		};
		return restartHandler;
	}

	/** buildInitialPropertyMap create the initial set of properties from the contents of launch.ini
	 * and for a few other properties necessary to launch defaults are supplied if not provided.
	 * The value '@null' will set the map value to null.
	 * @return a map containing the initial properties
	 */
	protected Map buildInitialPropertyMap() {
		Map initialPropertyMap = new HashMap();
		Properties launchProperties = loadProperties(resourceBase + LAUNCH_INI);
		for (Iterator it = launchProperties.entrySet().iterator(); it.hasNext();) {
			Map.Entry entry = (Map.Entry) it.next();
			String key = (String) entry.getKey();
			String value = (String) entry.getValue();
			setInitialProperty(initialPropertyMap, key, value);
		}

		try {
			// install.area if not specified
			if (initialPropertyMap.get(OSGI_INSTALL_AREA) == null)
				initialPropertyMap.put(OSGI_INSTALL_AREA, platformDirectory.toURL().toExternalForm());

			// configuration.area if not specified
			if (initialPropertyMap.get(OSGI_CONFIGURATION_AREA) == null) {
				File configurationDirectory = new File(platformDirectory, "configuration"); //$NON-NLS-1$
				if (!configurationDirectory.exists()) {
					configurationDirectory.mkdirs();
				}
				initialPropertyMap.put(OSGI_CONFIGURATION_AREA, configurationDirectory.toURL().toExternalForm());
			}

			// instance.area if not specified
			if (initialPropertyMap.get(OSGI_INSTANCE_AREA) == null) {
				File workspaceDirectory = new File(platformDirectory, "workspace"); //$NON-NLS-1$
				if (!workspaceDirectory.exists()) {
					workspaceDirectory.mkdirs();
				}
				initialPropertyMap.put(OSGI_INSTANCE_AREA, workspaceDirectory.toURL().toExternalForm());
			}

			// read values from config.ini
			Properties configurationProperties = loadConfigurationFile(initialPropertyMap);

			// osgi.framework if not specified
			if (initialPropertyMap.get(OSGI_FRAMEWORK) == null) {
				String osgiFramework = configurationProperties.getProperty(OSGI_FRAMEWORK);
				if (osgiFramework != null)
					initialPropertyMap.put(OSGI_FRAMEWORK, osgiFramework);
			}

			// osgi.framework.extensions if not specified
			if (initialPropertyMap.get(OSGI_FRAMEWORK_EXTENSIONS) == null) {
				String osgiFrameworkExtensions = configurationProperties.getProperty(OSGI_FRAMEWORK_EXTENSIONS);
				if (osgiFrameworkExtensions != null)
					initialPropertyMap.put(OSGI_FRAMEWORK_EXTENSIONS, osgiFrameworkExtensions);
			}

		} catch (MalformedURLException e) {
			throw new RuntimeException("Error establishing location"); //$NON-NLS-1$
		}

		return initialPropertyMap;
	}

	private void setInitialProperty(Map initialPropertyMap, String key, String value) {
		if (key.endsWith("*")) { //$NON-NLS-1$
			if (value.equals(NULL_IDENTIFIER)) {
				clearPrefixedSystemProperties(key.substring(0, key.length() - 1), initialPropertyMap);
			}
		} else if (value.equals(NULL_IDENTIFIER))
			initialPropertyMap.put(key, null);
		else
			initialPropertyMap.put(key, value);
	}

	private Properties loadConfigurationFile(Map initialPropertyMap) {
		InputStream is = null;
		try {
			String installArea = (String) initialPropertyMap.get(OSGI_INSTALL_AREA);
			if (installArea.startsWith(FILE_SCHEME)) {
				installArea = installArea.substring(FILE_SCHEME.length());
			}
			File installBase = new File(installArea);

			String configurationArea = (String) initialPropertyMap.get(OSGI_CONFIGURATION_AREA);
			if (configurationArea.startsWith(FILE_SCHEME)) {
				configurationArea = configurationArea.substring(FILE_SCHEME.length());
			}
			File configurationBase = new File(configurationArea);
			if (!configurationBase.isAbsolute())
				configurationBase = new File(installBase, configurationArea);

			File configurationFile = new File(configurationBase, CONFIG_INI);
			if (!configurationFile.exists())
				return null;

			Properties configProperties = new Properties();
			is = new BufferedInputStream(new FileInputStream(configurationFile));
			configProperties.load(is);
			return configProperties;
		} catch (Throwable t) {
			context.log("Error reading configuration file -- " + t.toString()); //$NON-NLS-1$
			return null;
		} finally {
			if (is != null)
				try {
					is.close();
				} catch (IOException e) {
					// unexpected
					e.printStackTrace();
				}
		}
	}

	/**
	 * clearPrefixedSystemProperties clears System Properties by writing null properties in the targetPropertyMap that match a prefix
	 */
	private static void clearPrefixedSystemProperties(String prefix, Map targetPropertyMap) {
		for (Iterator it = System.getProperties().keySet().iterator(); it.hasNext();) {
			String propertyName = (String) it.next();
			if (propertyName.startsWith(prefix) && !targetPropertyMap.containsKey(propertyName)) {
				targetPropertyMap.put(propertyName, null);
			}
		}
	}

	/**
	 * buildCommandLineArguments parses the commandline config parameter into a set of arguments 
	 * @return an array of String containing the commandline arguments
	 */
	protected String[] buildCommandLineArguments() {
		List args = new ArrayList();

		String commandLine = config.getInitParameter(CONFIG_COMMANDLINE);
		if (commandLine != null) {
			StringTokenizer tokenizer = new StringTokenizer(commandLine, WS_DELIM);
			while (tokenizer.hasMoreTokens()) {
				String arg = tokenizer.nextToken();
				if (arg.startsWith("\"")) { //$NON-NLS-1$
					if (arg.endsWith("\"")) { //$NON-NLS-1$ 
						if (arg.length() >= 2) {
							// strip the beginning and ending quotes 
							arg = arg.substring(1, arg.length() - 1);
						}
					} else {
						String remainingArg = tokenizer.nextToken("\""); //$NON-NLS-1$
						arg = arg.substring(1) + remainingArg;
						// skip to next whitespace separated token
						tokenizer.nextToken(WS_DELIM);
					}
				} else if (arg.startsWith("'")) { //$NON-NLS-1$
					if (arg.endsWith("'")) { //$NON-NLS-1$ 
						if (arg.length() >= 2) {
							// strip the beginning and ending quotes 
							arg = arg.substring(1, arg.length() - 1);
						}
					} else {
						String remainingArg = tokenizer.nextToken("'"); //$NON-NLS-1$
						arg = arg.substring(1) + remainingArg;
						// skip to next whitespace separated token
						tokenizer.nextToken(WS_DELIM);
					}
				} else if (arg.startsWith("-D")) { //$NON-NLS-1$
					int matchIndex = arg.indexOf("=\""); //$NON-NLS-1$
					if (matchIndex != -1) {
						if (!arg.substring(matchIndex + 2).endsWith("\"") && tokenizer.hasMoreTokens()) { //$NON-NLS-1$
							arg += tokenizer.nextToken("\"") + "\""; //$NON-NLS-1$ //$NON-NLS-2$
							// skip to next whitespace separated token
							tokenizer.nextToken(WS_DELIM);
						}
					}
				}
				args.add(arg);
			}
		}
		return (String[]) args.toArray(new String[] {});
	}

	/**
	 * stop is used to "shutdown" the framework and make it avialable for garbage collection.
	 * The default implementation also has special handling for Apache Commons Logging to "release" any
	 * resources associated with the frameworkContextClassLoader.
	 */
	public synchronized void stop() {
		if (platformDirectory == null) {
			context.log("Shutdown unnecessary. (not deployed)"); //$NON-NLS-1$
			return;
		}

		if (frameworkClassLoader == null) {
			context.log("Framework is already shutdown"); //$NON-NLS-1$
			return;
		}

		ClassLoader original = Thread.currentThread().getContextClassLoader();
		try {
			Class clazz = frameworkClassLoader.loadClass(STARTER);
			Method method = clazz.getDeclaredMethod("shutdown", (Class[]) null); //$NON-NLS-1$
			Thread.currentThread().setContextClassLoader(frameworkContextClassLoader);
			method.invoke(clazz, (Object[]) null);

			// ACL keys its loggers off of the ContextClassLoader which prevents GC without calling release. 
			// This section explicitly calls release if ACL is used.
			try {
				clazz = this.getClass().getClassLoader().loadClass("org.apache.commons.logging.LogFactory"); //$NON-NLS-1$
				method = clazz.getDeclaredMethod("release", new Class[] {ClassLoader.class}); //$NON-NLS-1$
				method.invoke(clazz, new Object[] {frameworkContextClassLoader});
			} catch (ClassNotFoundException e) {
				// ignore, ACL is not being used
			}

		} catch (Exception e) {
			context.log("Error while stopping Framework", e); //$NON-NLS-1$
			return;
		} finally {
			frameworkClassLoader.close();
			frameworkClassLoader = null;
			frameworkContextClassLoader = null;
			Thread.currentThread().setContextClassLoader(original);
		}
	}

	/**
	 * copyResource is a convenience method to recursively copy resources from the ServletContext to
	 * an installation target. The default behavior will create a directory if the resourcepath ends
	 * in '/' and a file otherwise.
	 * @param resourcePath - The resource root path
	 * @param target - The root location where resources are to be copied
	 */
	protected void copyResource(String resourcePath, File target) {
		if (resourcePath.endsWith("/")) { //$NON-NLS-1$
			target.mkdir();
			Set paths = context.getResourcePaths(resourcePath);
			if (paths == null)
				return;
			for (Iterator it = paths.iterator(); it.hasNext();) {
				String path = (String) it.next();
				File newFile = new File(target, path.substring(resourcePath.length()));
				copyResource(path, newFile);
			}
		} else {
			try {
				if (target.createNewFile()) {
					InputStream is = null;
					OutputStream os = null;
					try {
						is = context.getResourceAsStream(resourcePath);
						if (is == null)
							return;
						os = new FileOutputStream(target);
						byte[] buffer = new byte[8192];
						int bytesRead = is.read(buffer);
						while (bytesRead != -1) {
							os.write(buffer, 0, bytesRead);
							bytesRead = is.read(buffer);
						}
					} finally {
						if (is != null)
							is.close();

						if (os != null)
							os.close();
					}
				}
			} catch (IOException e) {
				context.log("Error copying resources", e); //$NON-NLS-1$
			}
		}
	}

	/**
	 * deleteDirectory is a convenience method to recursively delete a directory
	 * @param directory - the directory to delete.
	 * @return was the delete successful
	 */
	protected static boolean deleteDirectory(File directory) {
		if (directory.isDirectory()) {
			File[] files = directory.listFiles();
			for (int i = 0; i < files.length; i++) {
				if (files[i].isDirectory()) {
					deleteDirectory(files[i]);
				} else {
					files[i].delete();
				}
			}
		}
		return directory.delete();
	}

	/**
	 * Used when to set the ContextClassLoader when the BridgeServlet delegates to a Servlet
	 * inside the framework
	 * @return a Classloader with the OSGi framework's context class loader.
	 */
	public synchronized ClassLoader getFrameworkContextClassLoader() {
		return frameworkContextClassLoader;
	}

	/**
	 * Platfom Directory is where the OSGi software is installed
	 * @return the framework install location
	 */
	protected synchronized File getPlatformDirectory() {
		return platformDirectory;
	}

	/**
	 * loadProperties is a convenience method to load properties from a servlet context resource
	 * @param resource - The target to read properties from
	 * @return the properties
	 */
	protected Properties loadProperties(String resource) {
		Properties result = new Properties();
		InputStream in = null;
		try {
			URL location = context.getResource(resource);
			if (location != null) {
				in = location.openStream();
				result.load(in);
			}
		} catch (MalformedURLException e) {
			// no url to load from
		} catch (IOException e) {
			// its ok if there is no file
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
		return result;
	}

	/***************************************************************************
	 * See org.eclipse.core.launcher [copy of searchFor, findMax,
	 * compareVersion, getVersionElements] TODO: If these methods were made
	 * public and static we could use them directly
	 **************************************************************************/

	/**
	 * Searches for the given target directory starting in the "plugins" subdirectory
	 * of the given location.  If one is found then this location is returned; 
	 * otherwise an exception is thrown.
	 * @param target 
	 * 
	 * @return the location where target directory was found
	 * @param start the location to begin searching
	 */
	protected String searchFor(final String target, String start) {
		FileFilter filter = new FileFilter() {
			public boolean accept(File candidate) {
				return candidate.getName().equals(target) || candidate.getName().startsWith(target + "_"); //$NON-NLS-1$
			}
		};
		File[] candidates = new File(start).listFiles(filter);
		if (candidates == null)
			return null;
		String[] arrays = new String[candidates.length];
		for (int i = 0; i < arrays.length; i++) {
			arrays[i] = candidates[i].getName();
		}
		int result = findMax(arrays);
		if (result == -1)
			return null;
		return candidates[result].getAbsolutePath().replace(File.separatorChar, '/') + (candidates[result].isDirectory() ? "/" : ""); //$NON-NLS-1$//$NON-NLS-2$
	}

	protected int findMax(String[] candidates) {
		int result = -1;
		Object maxVersion = null;
		for (int i = 0; i < candidates.length; i++) {
			String name = candidates[i];
			String version = ""; //$NON-NLS-1$ // Note: directory with version suffix is always > than directory without version suffix
			int index = name.indexOf('_');
			if (index != -1)
				version = name.substring(index + 1);
			Object currentVersion = getVersionElements(version);
			if (maxVersion == null) {
				result = i;
				maxVersion = currentVersion;
			} else {
				if (compareVersion((Object[]) maxVersion, (Object[]) currentVersion) < 0) {
					result = i;
					maxVersion = currentVersion;
				}
			}
		}
		return result;
	}

	/**
	 * Compares version strings. 
	 * @param left 
	 * @param right 
	 * @return result of comparison, as integer;
	 * <code><0</code> if left < right;
	 * <code>0</code> if left == right;
	 * <code>>0</code> if left > right;
	 */
	private int compareVersion(Object[] left, Object[] right) {

		int result = ((Integer) left[0]).compareTo((Integer) right[0]); // compare major
		if (result != 0)
			return result;

		result = ((Integer) left[1]).compareTo((Integer) right[1]); // compare minor
		if (result != 0)
			return result;

		result = ((Integer) left[2]).compareTo((Integer) right[2]); // compare service
		if (result != 0)
			return result;

		return ((String) left[3]).compareTo((String) right[3]); // compare qualifier
	}

	/**
	 * Do a quick parse of version identifier so its elements can be correctly compared.
	 * If we are unable to parse the full version, remaining elements are initialized
	 * with suitable defaults.
	 * @param version 
	 * @return an array of size 4; first three elements are of type Integer (representing
	 * major, minor and service) and the fourth element is of type String (representing
	 * qualifier). Note, that returning anything else will cause exceptions in the caller.
	 */
	private Object[] getVersionElements(String version) {
		if (version.endsWith(DOT_JAR))
			version = version.substring(0, version.length() - 4);
		Object[] result = {new Integer(0), new Integer(0), new Integer(0), ""}; //$NON-NLS-1$
		StringTokenizer t = new StringTokenizer(version, "."); //$NON-NLS-1$
		String token;
		int i = 0;
		while (t.hasMoreTokens() && i < 4) {
			token = t.nextToken();
			if (i < 3) {
				// major, minor or service ... numeric values
				try {
					result[i++] = new Integer(token);
				} catch (Exception e) {
					// invalid number format - use default numbers (0) for the rest
					break;
				}
			} else {
				// qualifier ... string value
				result[i++] = token;
			}
		}
		return result;
	}

	/**
	 * The ChildFirstURLClassLoader alters regular ClassLoader delegation and will check the URLs
	 * used in its initialization for matching classes before delegating to it's parent.
	 * Sometimes also referred to as a ParentLastClassLoader
	 */
	protected class ChildFirstURLClassLoader extends CloseableURLClassLoader {

		public ChildFirstURLClassLoader(URL[] urls, ClassLoader parent) {
			super(urls, parent, false);
		}

		public URL getResource(String name) {
			URL resource = findResource(name);
			if (resource == null) {
				ClassLoader parent = getParent();
				if (parent != null)
					resource = parent.getResource(name);
			}
			return resource;
		}

		protected synchronized Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
			Class clazz = findLoadedClass(name);
			if (clazz == null) {
				try {
					clazz = findClass(name);
				} catch (ClassNotFoundException e) {
					ClassLoader parent = getParent();
					if (parent != null)
						clazz = parent.loadClass(name);
					else
						clazz = getSystemClassLoader().loadClass(name);
				}
			}

			if (resolve)
				resolveClass(clazz);

			return clazz;
		}

		// we want to ensure that the framework has AllPermissions
		protected PermissionCollection getPermissions(CodeSource codesource) {
			return allPermissions;
		}
	}

}
