package com.example.customerservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * SPA 转发：将 /user、/agent、/admin 页面路由统一转发到 Vue index.html，
 * 由前端路由接管。不拦截 REST API、actuator、附件下载、WebSocket 等路径。
 */
@Controller
public class FrontendForwardingController {

    private final String developmentServerUrl;

    public FrontendForwardingController() {
        this("");
    }

    public FrontendForwardingController(
            @Value("${app.frontend.dev-server-url:}") String developmentServerUrl
    ) {
        this.developmentServerUrl = developmentServerUrl == null ? "" : developmentServerUrl.trim();
    }

    private Object spaEntry() {
        if (!developmentServerUrl.isBlank()) {
            return new RedirectView(developmentServerUrl + "/login");
        }
        return "forward:/frontend/index.html";
    }

    @GetMapping({"/frontend", "/frontend/", "/frontend/login", "/frontend/user", "/frontend/user/**", "/user", "/user/**"})
    public Object userWorkspace() {
        return spaEntry();
    }

    @GetMapping({"/frontend/agent", "/frontend/agent/**", "/agent", "/agent/**"})
    public Object agentWorkspace() {
        return spaEntry();
    }

    @GetMapping({"/frontend/admin", "/frontend/admin/**", "/admin", "/admin/**"})
    public Object adminWorkspace() {
        return spaEntry();
    }
}
