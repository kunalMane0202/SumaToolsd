package com.sumasoft.tracking.rbac;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Puts the permission guard in front of every /api call (Step 39). */
@Configuration
public class RbacWebConfig implements WebMvcConfigurer {

    private final PermissionGuard guard;

    public RbacWebConfig(PermissionGuard guard) {
        this.guard = guard;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guard).addPathPatterns("/api/**");
    }
}