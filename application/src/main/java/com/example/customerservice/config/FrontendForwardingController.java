package com.example.customerservice.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 转发：将 /user、/agent、/admin 页面路由统一转发到 Vue index.html，
 * 由前端路由接管。不拦截 REST API、actuator、附件下载、WebSocket 等路径。
 */
@Controller
public class FrontendForwardingController {

    @GetMapping({"/frontend", "/frontend/", "/frontend/login", "/frontend/user", "/frontend/user/**", "/user", "/user/**"})
    public String userWorkspace() {
        return "forward:/frontend/index.html";
    }

    @GetMapping({"/frontend/agent", "/frontend/agent/**", "/agent", "/agent/**"})
    public String agentWorkspace() {
        return "forward:/frontend/index.html";
    }

    @GetMapping({"/frontend/admin", "/frontend/admin/**", "/admin", "/admin/**"})
    public String adminWorkspace() {
        return "forward:/frontend/index.html";
    }
}
