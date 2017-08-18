package com.viglet.turing.listener;

import javax.servlet.http.*;

import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.service.system.TurConfigVarService;

import com.viglet.turing.listener.onstartup.ml.TurMLVendorOnStartup;
import com.viglet.turing.listener.onstartup.nlp.TurNLPEntityOnStartup;
import com.viglet.turing.listener.onstartup.nlp.TurNLPFeatureOnStartup;
import com.viglet.turing.listener.onstartup.nlp.TurNLPInstanceOnStartup;
import com.viglet.turing.listener.onstartup.nlp.TurNLPVendorEntityOnStartup;
import com.viglet.turing.listener.onstartup.nlp.TurNLPVendorOnStartup;
import com.viglet.turing.listener.onstartup.se.TurSEInstanceOnStartup;
import com.viglet.turing.listener.onstartup.se.TurSEVendorOnStartup;
import com.viglet.turing.listener.onstartup.storage.TurDataGroupStartup;
import com.viglet.turing.listener.onstartup.ml.TurMLInstanceOnStartup;
import javax.servlet.*;

public class TurListener implements ServletContextListener, HttpSessionListener {
	final String FIRST_TIME = "FIRST_TIME";
	ServletContext servletContext;
	TurConfigVarService turConfigVarService = new TurConfigVarService();
	TurConfigVar turConfigVar = new TurConfigVar();

	/* A listener class must have a zero-argument constructor: */
	public TurListener() {
	}

	/* Methods from the ServletContextListener */
	public void contextInitialized(ServletContextEvent sce) {
		if (turConfigVarService.get(FIRST_TIME) == null) {
			servletContext = sce.getServletContext();
			System.out.println("Checking tables ...");
			TurNLPVendorOnStartup.createDefaultRows();
			TurNLPEntityOnStartup.createDefaultRows();
			TurNLPVendorEntityOnStartup.createDefaultRows();
			TurNLPFeatureOnStartup.createDefaultRows();
			TurNLPInstanceOnStartup.createDefaultRows();
			TurMLVendorOnStartup.createDefaultRows();
			TurMLInstanceOnStartup.createDefaultRows();
			TurSEVendorOnStartup.createDefaultRows();
			TurSEInstanceOnStartup.createDefaultRows();
			TurDataGroupStartup.createDefaultRows();
			System.out.println("Tables checked.");

			turConfigVar.setId(FIRST_TIME);
			turConfigVar.setPath("/system");
			turConfigVar.setValue("true");
			turConfigVarService.save(turConfigVar);
		}

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