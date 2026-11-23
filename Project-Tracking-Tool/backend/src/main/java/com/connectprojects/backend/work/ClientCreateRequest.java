package com.connectprojects.backend.work;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record ClientCreateRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 40) String code,
        @Size(max = 160) String industry,
        @Size(max = 120) String contactPerson,
        @Email @Size(max = 254) String email,
        @Size(max = 40) String phone,
        @Size(max = 160) String companyName,
        @Size(max = 500) String address,
        Long accountManagerId,
        Boolean active
) {
}
