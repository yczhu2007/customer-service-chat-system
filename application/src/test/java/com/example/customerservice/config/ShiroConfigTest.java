package com.example.customerservice.config;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ShiroConfigTest {

    @Test
    void allowsAnonymousAccessToPackagedFrontendAssets() {
        ShiroFilterFactoryBean factoryBean = new ShiroConfig()
                .shiroFilterFactoryBean(mock(SecurityManager.class));

        assertEquals(
                "anon",
                factoryBean.getFilterChainDefinitionMap().get("/frontend/**")
        );
    }
}
