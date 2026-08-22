package com.example.customerservice.config;

import java.security.Principal;
import java.util.Set;


/**
 * 表示当前经过Token认证的WebSocket用户。
 */
public class WebSocketUserPrincipal
        implements Principal {

    private final String name;

    private final Set<String> roleCodes;



    public WebSocketUserPrincipal(
            String name,
            Set<String> roleCodes
    ) {

        this.name =
                name;


        if (roleCodes == null) {

            this.roleCodes =
                    Set.of();

        } else {

            this.roleCodes =
                    Set.copyOf(
                            roleCodes
                    );
        }

    }


    /**
     * Principal名称使用真实userId。
     */
    @Override
    public String getName() {

        return name;
    }


    /**
     * 获取当前用户全部角色。
     */
    public Set<String> getRoleCodes() {

        return roleCodes;
    }


    /**
     * 判断当前WebSocket用户是否具有指定角色。
     */
    public boolean hasRole(
            String roleCode
    ) {

        return roleCodes.contains(
                roleCode
        );
    }


    @Override
    public String toString() {

        return "WebSocketUserPrincipal{" +
                "name='" + name + '\'' +
                ", roleCodes=" + roleCodes +
                '}';
    }
}
