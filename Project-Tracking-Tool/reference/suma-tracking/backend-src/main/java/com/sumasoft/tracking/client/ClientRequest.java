package com.sumasoft.tracking.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * What the user sends to CREATE or EDIT a client.
 * Every field is checked before anything is saved.
 * (Status is not here: use the activate / deactivate addresses.)
 */
public record ClientRequest(

        @NotBlank(message = "Client name is required")
        @Size(max = 150, message = "Client name must be at most 150 characters")
        String clientName,

        @Size(max = 200, message = "Company name must be at most 200 characters")
        String companyName,

        @Size(max = 150, message = "Contact person must be at most 150 characters")
        String contactPerson,

        @Email(message = "Email is not valid")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @Pattern(regexp = "^[0-9+()\\- ]{6,30}$",
                 message = "Contact number may contain only digits, spaces, + - ( ) and must be 6 to 30 characters")
        String contactNumber,

        @Size(max = 1000, message = "Address must be at most 1000 characters")
        String address,

        @Size(max = 100, message = "Business domain must be at most 100 characters")
        String businessDomain,

        LocalDate onboardingDate,

        @Size(max = 2000, message = "Remarks must be at most 2000 characters")
        String remarks
) {
}