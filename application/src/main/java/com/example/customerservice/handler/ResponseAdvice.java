package com.example.customerservice.handler;

import com.example.customerservice.common.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 自动包装Controller的成功返回结果。
 */
@RestControllerAdvice(
        basePackages =
                "com.example.customerservice.controller"
)
public class ResponseAdvice
        implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>>
                    converterType
    ) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>>
                    selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        /*
         * 已经是Result时不再重复包装。
         */
        if (body instanceof Result<?>) {
            return body;
        }

        int status = 200;

        if (response
                instanceof ServletServerHttpResponse
                servletResponse) {
            status = servletResponse
                    .getServletResponse()
                    .getStatus();
        }

        String message =
                status == 201
                        ? "创建成功"
                        : "操作成功";

        return Result.success(
                status,
                message,
                body
        );
    }
}