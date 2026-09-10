package com.example.customerservice.service.impl;

import com.example.customerservice.constant.AccountStatus;

import com.example.customerservice.domain.SysPermission;
import com.example.customerservice.dto.PermissionVO;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** Shared RBAC request normalization and permission view mapping. */
abstract class RbacServiceSupport {

    protected PermissionVO toPermissionVO(
            SysPermission permission
    ) {
        PermissionVO permissionVO =
                new PermissionVO();

        permissionVO.setId(permission.getId());
        permissionVO.setPermissionCode(
                permission.getPermissionCode()
        );
        permissionVO.setPermissionName(
                permission.getPermissionName()
        );
        permissionVO.setPermissionType(
                permission.getPermissionType()
        );
        permissionVO.setRequestMethod(
                permission.getRequestMethod()
        );
        permissionVO.setRequestPath(
                permission.getRequestPath()
        );
        permissionVO.setDescription(
                permission.getDescription()
        );
        permissionVO.setStatus(
                permission.getStatus()
        );
        permissionVO.setCreateTime(
                permission.getCreateTime()
        );
        permissionVO.setUpdateTime(
                permission.getUpdateTime()
        );

        return permissionVO;
    }

    protected String requireText(
            String value,
            String message
    ) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }

    protected String normalizeStatus(
            String status,
            String defaultStatus
    ) {
        if (!StringUtils.hasText(status)) {
            return defaultStatus;
        }

        String normalizedStatus =
                status.trim().toUpperCase(Locale.ROOT);

        if (
                !AccountStatus.ENABLED.equals(normalizedStatus) &&
                        !AccountStatus.DISABLED.equals(normalizedStatus)
        ) {
            throw new IllegalArgumentException(
                    "status只能是ENABLED或DISABLED"
            );
        }

        return normalizedStatus;
    }

    protected String normalizePermissionType(
            String permissionType,
            String defaultType
    ) {
        if (!StringUtils.hasText(permissionType)) {
            return defaultType;
        }

        String normalizedType =
                permissionType.trim()
                        .toUpperCase(Locale.ROOT);

        if (
                !"API".equals(normalizedType) &&
                        !"MENU".equals(normalizedType) &&
                        !"BUTTON".equals(normalizedType)
        ) {
            throw new IllegalArgumentException(
                    "permissionType只能是API、MENU或BUTTON"
            );
        }

        return normalizedType;
    }

    protected String normalizeRequestMethod(
            String requestMethod
    ) {
        if (!StringUtils.hasText(requestMethod)) {
            return null;
        }

        return requestMethod.trim()
                .toUpperCase(Locale.ROOT);
    }

    protected String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }
}
