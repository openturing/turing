package com.viglet.turing.api.auth;


import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TurKC {
    /**
     * Makes SSO Logout.
     * This endpoint has to be private. Otherwise there will be no token to send logout to KeyCloak.
     *
     * @param request the request
     * @return redirect to logout page
     * @throws ServletException if tomcat session logout throws exception
     */
    @GetMapping(path = "/logoutkc")
    public String logout(HttpServletRequest request) throws ServletException {
        keycloakSessionLogout(request);
        tomcatSessionLogout(request);
        return "redirect:/";
    }

    private void keycloakSessionLogout(HttpServletRequest request){
        RefreshableKeycloakSecurityContext c = getKeycloakSecurityContext(request);
        if (c != null) {
            KeycloakDeployment d = c.getDeployment();
            c.logout(d);
        }
    }

    private void tomcatSessionLogout(HttpServletRequest request) throws ServletException {
        request.logout();
    }

    private RefreshableKeycloakSecurityContext getKeycloakSecurityContext(HttpServletRequest request){
        return (RefreshableKeycloakSecurityContext) request.getAttribute(KeycloakSecurityContext.class.getName());
    }
}