package com.sumasoft.tracking.rbac;

import com.sumasoft.tracking.auth.AccessService;
import com.sumasoft.tracking.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks the role's permission on EVERY /api call before the controller runs (Step 39).
 * This is on the server, so typing an address in the browser or calling the API directly
 * does not get around it. Refused calls answer 403 with a clear message.
 * The services keep their own checks too (own projects only, assignee rules), so both must agree.
 */
@Component
public class PermissionGuard implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PermissionGuard.class);
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    private final AccessService access;
    private final RbacService rbac;

    public PermissionGuard(AccessService access, RbacService rbac) {
        this.access = access;
        this.rbac = rbac;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/api/")) {
            return true;
        }
        RouteRules.Rule rule = RouteRules.find(method, path);
        if (rule == null) {
            if (warned.add(method + " " + path)) {
                log.warn("No permission rule for {} {}; only sign-in and the service's own checks apply", method, path);
            }
            return true;
        }
        if (rule.anyOf().isEmpty()) {
            return true;
        }
        AuthenticatedUser me;
        try {
            me = access.current();
        } catch (RuntimeException notSignedIn) {
            return true; // Spring Security answers 401 for calls without a valid sign-in
        }
        if (me == null || rbac.has(me, rule.anyOf().toArray(String[]::new))) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Your role does not allow you to "
                + rule.action() + ".\"}");
        return false;
    }
}