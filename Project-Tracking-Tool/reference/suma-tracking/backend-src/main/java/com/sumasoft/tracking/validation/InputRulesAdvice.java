package com.sumasoft.tracking.validation;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Step 36: a safety net for every form that is sent to the backend (employees, clients, ...).
 * Whatever the screen, a JSON body field called
 *   email          must be a real email address (name@company.com)
 *   contactNumber  must be exactly 10 digits
 *   fullName       must be a person's name
 *   employeeCode   must be letters / digits / hyphen
 * Empty values are left to the form's own "required" rules.
 * The login request is not checked here, so a typo there still gives the normal
 * "wrong email or password" answer.
 * The answer has the same shape as the other form errors: { status, message, fieldErrors }.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InputRulesAdvice extends RequestBodyAdviceAdapter {

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (body == null || isLoginRequest()) {
            return body;
        }
        Map<String, String> errors = new LinkedHashMap<>();
        String email = read(body, "email");
        if (notBlank(email) && !InputRules.isEmail(email)) {
            errors.put("email", InputRules.EMAIL_MESSAGE);
        }
        String phone = read(body, "contactNumber");
        if (notBlank(phone) && !InputRules.isPhone(phone)) {
            errors.put("contactNumber", InputRules.PHONE_MESSAGE);
        }
        String fullName = read(body, "fullName");
        if (notBlank(fullName) && !InputRules.isPersonName(fullName)) {
            errors.put("fullName", InputRules.PERSON_NAME_MESSAGE);
        }
        String code = read(body, "employeeCode");
        if (notBlank(code) && !InputRules.isEmployeeCode(code)) {
            errors.put("employeeCode", InputRules.EMP_CODE_MESSAGE);
        }
        if (!errors.isEmpty()) {
            throw new InvalidInputException(errors);
        }
        return body;
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<Map<String, Object>> onInvalidInput(InvalidInputException e) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("status", HttpStatus.BAD_REQUEST.value());
        answer.put("message", "Please correct the fields marked in red and try again.");
        answer.put("fieldErrors", e.fieldErrors());
        return ResponseEntity.badRequest().body(answer);
    }

    private static boolean isLoginRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRequestURI() != null && request.getRequestURI().startsWith("/api/auth/");
        }
        return false;
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    /** Reads a String property from a record or a plain class; null when there is none. */
    private static String read(Object body, String name) {
        Class<?> type = body.getClass();
        try {
            if (type.isRecord()) {
                for (RecordComponent c : type.getRecordComponents()) {
                    if (c.getName().equals(name) && c.getType() == String.class) {
                        var accessor = c.getAccessor();
                        accessor.setAccessible(true);
                        return (String) accessor.invoke(body);
                    }
                }
                return null;
            }
            for (Class<?> t = type; t != null && t != Object.class; t = t.getSuperclass()) {
                for (Field f : t.getDeclaredFields()) {
                    if (f.getName().equals(name) && f.getType() == String.class) {
                        f.setAccessible(true);
                        return (String) f.get(body);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    /** Thrown when a form value breaks a rule; becomes a 400 answer with the field messages. */
    public static class InvalidInputException extends RuntimeException {
        private final Map<String, String> fieldErrors;

        public InvalidInputException(Map<String, String> fieldErrors) {
            super("Invalid input");
            this.fieldErrors = Map.copyOf(fieldErrors);
        }

        public Map<String, String> fieldErrors() {
            return fieldErrors;
        }
    }
}