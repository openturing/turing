package com.viglet.turing.listener;

import javax.servlet.http.*;

import com.viglet.turing.listener.onstartup.TurEntityOnStartup;
import com.viglet.turing.listener.onstartup.TurNLPFeatureOnStartup;
import com.viglet.turing.listener.onstartup.TurNLPInstanceOnStartup;
import com.viglet.turing.listener.onstartup.TurNLPVendorEntityOnStartup;
import com.viglet.turing.listener.onstartup.TurNLPVendorOnStartup;

import javax.servlet.*;

public class TurListener implements ServletContextListener, HttpSessionListener {
	/* A listener class must have a zero-argument constructor: */
	public TurListener() {
	}

	ServletContext servletContext;

	/* Methods from the ServletContextListener */
	public void contextInitialized(ServletContextEvent sce) {
		servletContext = sce.getServletContext();
		System.out.println("Checking tables ...");
		TurNLPVendorOnStartup.createDefaultRows();
		TurEntityOnStartup.createDefaultRows();
		TurNLPVendorEntityOnStartup.createDefaultRows();
		TurNLPFeatureOnStartup.createDefaultRows();
		TurNLPInstanceOnStartup.createDefaultRows();

		System.out.println("Tables checked.");
	}

	public void contextDestroyed(ServletContextEvent sce) {
	}

	/* Methods for the HttpSessionListener */
	public void sessionCreated(HttpSessionEvent hse) {
		log("CREATE", hse);
	}

	public void sessionDestroyed(HttpSessionEvent hse) {

		HttpSession _session = hse.getSession();
		long _start = _session.getCreationTime();
		long _end = _session.getLastAccessedTime();
		String _counter = (String) _session.getAttribute("counter");
		log("DESTROY, Session Duration:" + (_end - _start) + "(ms) Counter:" + _counter, hse);
	}

	protected void log(String msg, HttpSessionEvent hse) {
		String _ID = hse.getSession().getId();
		log("SessionID:" + _ID + "    " + msg);
	}

	protected void log(String msg) {
		System.out.println("[" + getClass().getName() + "] " + msg);
	}
}