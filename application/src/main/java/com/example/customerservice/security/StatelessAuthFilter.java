package com.example.customerservice.security;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.AccessControlFilter;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Shiro无状态Token过滤器。
 */
public class StatelessAuthFilter
        extends AccessControlFilter {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    private static final String
            AUTHORIZATION_HEADER =
            "Authorization";


    private static final String
            BEARER_PREFIX =
            "Bearer ";


    /**
     * OPTIONS预检请求直接放行。
     *
     * 其他请求进入onAccessDenied进行Token认证。
     */
    @Override
    protected boolean isAccessAllowed(
            ServletRequest request,
            ServletResponse response,
            Object mappedValue
    ) {

        HttpServletRequest httpRequest =
                (HttpServletRequest)
                        request;


        return "OPTIONS".equalsIgnoreCase(
                httpRequest.getMethod()
        );
    }


    /**
     * 读取并认证Token。
     */
    @Override
    protected boolean onAccessDenied(
            ServletRequest request,
            ServletResponse response
    ) throws Exception {

        HttpServletRequest httpRequest =
                (HttpServletRequest)
                        request;


        HttpServletResponse httpResponse =
                (HttpServletResponse)
                        response;


        String token =
                extractToken(
                        httpRequest
                );


        if (
                token == null ||
                        token.isBlank()
        ) {

            writeUnauthorized(
                    httpResponse,
                    "缺少访问Token"
            );

            return false;
        }


        try {

            Subject subject =
                    getSubject(
                            request,
                            response
                    );


            subject.login(
                    new TokenAuthToken(
                            token
                    )
            );


            return true;

        } catch (AuthenticationException e) {

            writeUnauthorized(
                    httpResponse,
                    "Token无效、已过期或用户不可用"
            );

            return false;
        }
    }


    /**
     * 从Authorization请求头读取Bearer Token。
     */
    private String extractToken(
            HttpServletRequest request
    ) {

        String authorization =
                request.getHeader(
                        AUTHORIZATION_HEADER
                );


        if (
                authorization == null ||
                        authorization.isBlank()
        ) {

            return null;
        }


        if (
                !authorization.startsWith(
                        BEARER_PREFIX
                )
        ) {

            return null;
        }


        String token =
                authorization.substring(
                        BEARER_PREFIX.length()
                );


        if (token.isBlank()) {

            return null;
        }


        return token.trim();
    }


    private void writeUnauthorized(
            HttpServletResponse response,
            String message
    ) throws IOException {
        response.setStatus(
                HttpServletResponse.SC_UNAUTHORIZED
        );

        response.setCharacterEncoding(
                "UTF-8"
        );

        response.setContentType(
                "application/json;charset=UTF-8"
        );

        Map<String, Object> body =
                new LinkedHashMap<>();

        body.put("code", 401);
        body.put("message", message);
        body.put("data", null);
        body.put("time", LocalDateTime.now().toString());

        response.getWriter().write(
                OBJECT_MAPPER.writeValueAsString(body)
        );
    }
}