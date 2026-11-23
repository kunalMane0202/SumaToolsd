package com.sumasoft.tracking.user;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Web addresses (API) for roles and employees.
 *   GET  /api/roles                  -> the 8 roles
 *   GET  /api/users                  -> all employees
 *   GET  /api/users/{id}             -> one employee
 *   POST /api/users                  -> create an employee
 *   PUT  /api/users/{id}             -> edit an employee
 *   POST /api/users/{id}/activate    -> make ACTIVE
 *   POST /api/users/{id}/deactivate  -> make INACTIVE (nothing is deleted)
 * NOTE: no login yet. Login and permissions are added in Phase 4.
 */
@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/roles")
    public List<RoleResponse> listRoles() {
        return userService.listRoles();
    }

    @GetMapping("/users")
    public List<UserResponse> listUsers() {
        return userService.listUsers();
    }

    @GetMapping("/users/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return userService.getUser(id);
    }

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        UserResponse created = userService.createUser(request);
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }

    @PutMapping("/users/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return userService.updateUser(id, request);
    }

    @PostMapping("/users/{id}/activate")
    public UserResponse activateUser(@PathVariable Long id) {
        return userService.activateUser(id);
    }

    @PostMapping("/users/{id}/deactivate")
    public UserResponse deactivateUser(@PathVariable Long id) {
        return userService.deactivateUser(id);
    }
}