package com.sumasoft.tracking.user;

/**
 * What the API sends back for one role.
 */
public record RoleResponse(String code, String name, String userGroup, String description) {

    public static RoleResponse from(Role r) {
        return new RoleResponse(r.getCode(), r.getName(), r.getUserGroup(), r.getDescription());
    }
}