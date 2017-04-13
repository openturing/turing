/*******************************************************************************
 * Copyright (c) 2005, 2009 Cognos Incorporated, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Cognos Incorporated - initial API and implementation
 *     IBM Corporation - bug fixes and enhancements
 *******************************************************************************/
package org.eclipse.equinox.servletbridge;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.*;

/**
 * The BridgeServlet provides a means to bridge the servlet and OSGi 
 * runtimes. This class has 3 main responsibilities:
 * 1) Control the lifecycle of the associated FrameworkLauncher in line with its own lifecycle
 * 2) Provide a servlet "hook" that allows all servlet requests to be delegated to the registered servlet
 * 3) Provide means to manually control the framework lifecycle
 */
public class BridgeServlet extends HttpServlet {

	static final String INCLUDE_REQUEST_URI_ATTRIBUTE = "javax.servlet.include.request_uri"; //$NON-NLS-1$
	static final String INCLUDE_SERVLET_PATH_ATTRIBUTE = "javax.servlet.include.servlet_path"; //$NON-NLS-1$
	static final String INCLUDE_PATH_INFO_ATTRIBUTE = "javax.servlet.include.path_info"; //$NON-NLS-1$

	private static final long serialVersionUID = 2825667412474494674L;
	private static BridgeServlet instance;
	private HttpServlet delegate;
	private FrameworkLauncher framework;
	private int delegateReferenceCount;
	private boolean enableFrameworkControls;

	/**
	 * init() is called by the Servlet Container and used to instantiate the frameworkLauncher which MUST be an instance of FrameworkLauncher.
	 * After instantiating the framework init, deploy, and start are called.
	 */
	public void init() throws ServletException {
		super.init();

		String enableFrameworkControlsParameter = getServletConfig().getInitParameter("enableFrameworkControls"); //$NON-NLS-1$
		enableFrameworkControls = (enableFrameworkControlsParameter != null && enableFrameworkControlsParameter.equals("true")); //$NON-NLS-1$

		String frameworkLauncherClassParameter = getServletConfig().getInitParameter("frameworkLauncherClass"); //$NON-NLS-1$
		if (frameworkLauncherClassParameter != null) {
			try {
				Class frameworkLauncherClass = this.getClass().getClassLoader().loadClass(frameworkLauncherClassParameter);
				framework = (FrameworkLauncher) frameworkLauncherClass.newInstance();
			} catch (Exception e) {
				throw new ServletException(e);
			}
		} else {
			framework = new FrameworkLauncher();
		}

		boolean frameworkStarted = false;
		setInstance(this);
		try {
			framework.init(getServletConfig());
			framework.deploy();
			framework.start();
			frameworkStarted = true;
		} finally {
			if (!frameworkStarted)
				setInstance(null);
		}
	}

	/**
	 * destroy() is called by the Servlet Container and used to first stop and then destroy the framework.
	 */
	public void destroy() {
		framework.stop();
		framework.destroy();
		setInstance(null);
		super.destroy();
	}

	/**
	 * service is called by the Servlet Container and will first determine if the request is a
	 * framework control and will otherwise try to delegate to the registered servlet delegate
	 *  
	 */
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String pathInfo = req.getPathInfo();
		// Check if this is being handled by an extension mapping
		if (pathInfo == null && isExtensionMapping(req.getServletPath()))
			req = new ExtensionMappingRequest(req);

		if (req.getAttribute(INCLUDE_REQUEST_URI_ATTRIBUTE) == null) {
			if (enableFrameworkControls) {
				if (pathInfo != null && pathInfo.startsWith("/sp_")) { //$NON-NLS-1$
					if (serviceFrameworkControls(req, resp)) {
						return;
					}
				}
			}
		} else {
			String includePathInfo = (String) req.getAttribute(INCLUDE_PATH_INFO_ATTRIBUTE);
			// Check if this is being handled by an extension mapping
			if (includePathInfo == null || includePathInfo.length() == 0) {
				String servletPath = (String) req.getAttribute(INCLUDE_SERVLET_PATH_ATTRIBUTE);
				if (isExtensionMapping(servletPath))
					req = new IncludedExtensionMappingRequest(req);
			}
		}

		ClassLoader original = Thread.currentThread().getContextClassLoader();
		HttpServlet servletReference = acquireDelegateReference();
		if (servletReference == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND, "BridgeServlet: " + req.getRequestURI()); //$NON-NLS-1$
			return;
		}
		try {
			Thread.currentThread().setContextClassLoader(framework.getFrameworkContextClassLoader());
			servletReference.service(req, resp);
		} finally {
			releaseDelegateReference();
			Thread.currentThread().setContextClassLoader(original);
		}
	}

	private boolean isExtensionMapping(String servletPath) {
		if (servletPath == null)
			return false;

		String lastSegment = servletPath;
		int lastSlash = servletPath.lastIndexOf('/');
		if (lastSlash != -1)
			lastSegment = servletPath.substring(lastSlash + 1);

		return lastSegment.indexOf('.') != -1;
	}

	/**
	 * serviceFrameworkControls currently supports the following commands (identified by the request's pathinfo)
	 * sp_deploy - Copies the contents of the Equinox application to the install area 
	 * sp_undeploy - Removes the copy of the Equinox application from the install area
	 * sp_redeploy - Resets the platform (e.g. stops, undeploys, deploys, starts)
	 * sp_start - Starts a deployed platform
	 * sp_stop - Stops the platform 
	 */
	private boolean serviceFrameworkControls(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String pathInfo = req.getPathInfo();
		if (pathInfo.equals("/sp_start")) { //$NON-NLS-1$
			framework.start();
			resp.getWriter().write("Platform Started"); //$NON-NLS-1$
			return true;
		} else if (pathInfo.equals("/sp_stop")) { //$NON-NLS-1$
			framework.stop();
			resp.getWriter().write("Platform Stopped"); //$NON-NLS-1$
			return true;
		} else if (pathInfo.equals("/sp_deploy")) { //$NON-NLS-1$
			framework.deploy();
			resp.getWriter().write("Platform Deployed"); //$NON-NLS-1$
			return true;
		} else if (pathInfo.equals("/sp_undeploy")) { //$NON-NLS-1$
			framework.undeploy();
			resp.getWriter().write("Platform Undeployed"); //$NON-NLS-1$
			return true;
		} else if (pathInfo.equals("/sp_reset")) { //$NON-NLS-1$
			framework.stop();
			framework.start();
			resp.getWriter().write("Platform Reset"); //$NON-NLS-1$
			return true;
		} else if (pathInfo.equals("/sp_redeploy")) { //$NON-NLS-1$
			framework.stop();
			framework.undeploy();
			framework.deploy();
			framework.start();
			resp.getWriter().write("Platform Redeployed"); //$NON-NLS-1$
			return true;
		} else if (pathInfo.equals("/sp_test")) { //$NON-NLS-1$
			if (delegate == null)
				resp.getWriter().write("Servlet delegate not registered."); //$NON-NLS-1$
			else
				resp.getWriter().write("Servlet delegate registered - " + delegate.getClass().getName()); //$NON-NLS-1$
			return true;
		}
		return false;
	}

	private static synchronized void setInstance(BridgeServlet servlet) {
		if ((instance != null) && (servlet != null))
			throw new IllegalStateException("instance already set"); //$NON-NLS-1$
		instance = servlet;
	}

	private synchronized void releaseDelegateReference() {
		--delegateReferenceCount;
		notifyAll();
	}

	private synchronized HttpServlet acquireDelegateReference() {
		if (delegate != null)
			++delegateReferenceCount;
		return delegate;
	}

	/**
	 * registerServletDelegate is the hook method called from inside the OSGi runtime to register
	 * a servlet for which all future servlet calls will be delegated. If not null and no delegate
	 * is currently registered, init(ServletConfig) will be called on the servletDelegate before
	 * returning.
	 * @param servletDelegate - the servlet to register for delegation
	 */
	public static synchronized void registerServletDelegate(HttpServlet servletDelegate) {
		if (instance == null) {
			// shutdown already
			return;
		}

		if (servletDelegate == null)
			throw new NullPointerException("cannot register a null servlet delegate"); //$NON-NLS-1$

		synchronized (instance) {
			if (instance.delegate != null)
				throw new IllegalStateException("A Servlet Proxy is already registered"); //$NON-NLS-1$

			try {
				servletDelegate.init(instance.getServletConfig());
			} catch (ServletException e) {
				instance.getServletContext().log("Error initializing servlet delegate", e); //$NON-NLS-1$
				return;
			}
			instance.delegate = servletDelegate;
		}
	}

	/**
	 * unregisterServletDelegate is the hook method called from inside the OSGi runtime to unregister a delegate.
	 * If the servletDelegate matches the current registered delegate destroy() is called on the servletDelegate.
	 * destroy() will not be called until the delegate is finished servicing any previous requests.
	 * @param servletDelegate - the servlet to unregister
	 */
	public static synchronized void unregisterServletDelegate(HttpServlet servletDelegate) {
		if (instance == null) {
			// shutdown already
			return;
		}

		synchronized (instance) {
			if (instance.delegate == null)
				throw new IllegalStateException("No servlet delegate is registered"); //$NON-NLS-1$

			if (instance.delegate != servletDelegate)
				throw new IllegalStateException("Servlet delegate does not match registered servlet delegate"); //$NON-NLS-1$

			HttpServlet oldProxy = instance.delegate;
			instance.delegate = null;
			while (instance.delegateReferenceCount != 0) {
				try {
					instance.wait();
				} catch (InterruptedException e) {
					// keep waiting for all requests to finish
				}
			}
			oldProxy.destroy();
		}
	}

	static class ExtensionMappingRequest extends HttpServletRequestWrapper {

		public ExtensionMappingRequest(HttpServletRequest req) {
			super(req);
		}

		public String getPathInfo() {
			return super.getServletPath();
		}

		public String getServletPath() {
			return ""; //$NON-NLS-1$
		}
	}

	static class IncludedExtensionMappingRequest extends HttpServletRequestWrapper {

		public IncludedExtensionMappingRequest(HttpServletRequest req) {
			super(req);
		}

		public Object getAttribute(String attributeName) {
			if (attributeName.equals(INCLUDE_SERVLET_PATH_ATTRIBUTE)) {
				return ""; //$NON-NLS-1$
			} else if (attributeName.equals(INCLUDE_PATH_INFO_ATTRIBUTE)) {
				String servletPath = (String) super.getAttribute(INCLUDE_SERVLET_PATH_ATTRIBUTE);
				return servletPath;
			}
			return super.getAttribute(attributeName);
		}
	}

}