package com.example.customerservice.exception;


/**
 * 请求的业务数据不存在。
 */
public class NotFoundException
        extends RuntimeException {

    public NotFoundException(
            String message
    ) {

        super(
                message
        );
    }
}
