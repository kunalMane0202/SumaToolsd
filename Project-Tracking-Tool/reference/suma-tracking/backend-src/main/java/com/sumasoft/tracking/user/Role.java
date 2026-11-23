package com.sumasoft.tracking.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row of the "roles" table (created and filled by Flyway file V3).
 * Read-only in the program: roles are changed only through Flyway files.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    private Long id;

    @Column(name = "code", nullable = false, length = 40, insertable = false, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 100, insertable = false, updatable = false)
    private String name;

    /** SUPER_ADMIN, PROJECT_MANAGER or SUPPORT_USER (the 3 user types in the architecture diagram). */
    @Column(name = "user_group", nullable = false, length = 30, insertable = false, updatable = false)
    private String userGroup;

    @Column(name = "description", columnDefinition = "text", insertable = false, updatable = false)
    private String description;

    @Column(name = "sort_order", nullable = false, insertable = false, updatable = false)
    private Integer sortOrder;

    /** Needed by JPA. Not for normal use. */
    protected Role() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getUserGroup() { return userGroup; }
    public String getDescription() { return description; }
    public Integer getSortOrder() { return sortOrder; }
}