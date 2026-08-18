package com.example.customerservice.handler;

import com.example.customerservice.common.Result;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
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
        /*
         * 文件下载必须由ResourceHttpMessageConverter直接写出二进制内容。
         * 若包装为Result，会导致已经按Resource选定的转换器发生类型转换异常，
         * 浏览器只能收到一小段错误响应，附件因此无法显示或下载。
         */
        return !ResourceHttpMessageConverter.class.isAssignableFrom(converterType);
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
