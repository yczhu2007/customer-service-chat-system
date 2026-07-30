package com.example.customerservice.config;

import com.example.customerservice.security.StatelessAuthFilter;
import com.example.customerservice.security.TokenRealm;

import jakarta.servlet.Filter;

import org.apache.shiro.mgt.DefaultSessionStorageEvaluator;
import org.apache.shiro.mgt.DefaultSubjectDAO;
import org.apache.shiro.mgt.SecurityManager;

import org.apache.shiro.spring.web.ShiroFilterFactoryBean;

import org.apache.shiro.web.mgt.DefaultWebSecurityManager;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Shiro无状态Token认证配置。
 */
@Configuration
public class ShiroConfig {

    /**
     * 创建Shiro安全管理器。
     */
    @Bean
    public SecurityManager securityManager(
            TokenRealm tokenRealm
    ) {

        DefaultWebSecurityManager securityManager =
                new DefaultWebSecurityManager();


        securityManager.setRealm(
                tokenRealm
        );


        /*
         * 关闭Shiro Session。
         *
         * 每个HTTP请求都必须重新携带Token。
         */
        DefaultSessionStorageEvaluator
                sessionStorageEvaluator =
                new DefaultSessionStorageEvaluator();


        sessionStorageEvaluator
                .setSessionStorageEnabled(
                        false
                );


        DefaultSubjectDAO subjectDAO =
                new DefaultSubjectDAO();


        subjectDAO.setSessionStorageEvaluator(
                sessionStorageEvaluator
        );


        securityManager.setSubjectDAO(
                subjectDAO
        );


        return securityManager;
    }


    /**
     * 配置Shiro过滤器。
     */
    @Bean(name = "shiroFilter")
    public ShiroFilterFactoryBean
    shiroFilterFactoryBean(
            SecurityManager securityManager
    ) {

        ShiroFilterFactoryBean factoryBean =
                new ShiroFilterFactoryBean();


        factoryBean.setSecurityManager(
                securityManager
        );


        /*
         * 直接在Shiro内部创建Token过滤器。
         *
         * 不把StatelessAuthFilter声明为独立Spring Bean，
         * 防止Spring Boot将它注册成全局过滤器。
         */
        Map<String, Filter> filters =
                new LinkedHashMap<>();


        filters.put(
                "token",
                new StatelessAuthFilter()
        );


        factoryBean.setFilters(
                filters
        );


        /*
         * 必须使用LinkedHashMap，
         * 保证接口按照添加顺序匹配。
         */
        Map<String, String> filterChain =
                new LinkedHashMap<>();


        /*
         * 登录接口允许匿名访问。
         */
        filterChain.put(
                "/chat/login",
                "anon"
        );


        /*
         * 健康检查允许匿名访问。
         */
        filterChain.put(
                "/actuator/health",
                "anon"
        );


        /*
         * WebSocket 握手请求不经过 HTTP Token 过滤器，
         * 由 WebSocket 握手拦截器负责校验 Token。
         */
        filterChain.put(
                "/ws/chat",
                "anon"
        );


        /*
         * 测试页面允许匿名访问。
         */
        filterChain.put(
                "/stomp-test.html",
                "anon"
        );


        /*
         * Spring错误处理地址允许匿名访问。
         */
        filterChain.put(
                "/error",
                "anon"
        );


        /*
         * 其他全部HTTP接口必须携带Token。
         */
        filterChain.put(
                "/**",
                "token"
        );


        factoryBean.setFilterChainDefinitionMap(
                filterChain
        );


        return factoryBean;
    }
}
