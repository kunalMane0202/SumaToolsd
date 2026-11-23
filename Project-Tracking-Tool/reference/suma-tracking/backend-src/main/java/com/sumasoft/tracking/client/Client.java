package com.sumasoft.tracking.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One row of the "clients" table (created by Flyway file V1).
 * This class only describes the table. It never creates or changes it.
 */
@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_name", nullable = false, length = 150)
    private String clientName;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "contact_person", length = 150)
    private String contactPerson;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "contact_number", length = 30)
    private String contactNumber;

    @Column(name = "address", columnDefinition = "text")
    private String address;

    @Column(name = "business_domain", length = 100)
    private String businessDomain;

    /** ACTIVE or INACTIVE (the database CHECK rule in V1 allows only these). */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "onboarding_date")
    private LocalDate onboardingDate;

    @Column(name = "remarks", columnDefinition = "text")
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /** Needed by JPA. Not for normal use. */
    protected Client() {
    }

    /** Make a new client from checked input. New clients start as ACTIVE. */
    public static Client create(ClientRequest request) {
        Client client = new Client();
        client.applyDetails(request);
        client.status = "ACTIVE";
        return client;
    }

    /** Copy the editable fields from checked input. Empty text is saved as "no value". */
    public void applyDetails(ClientRequest request) {
        this.clientName = clean(request.clientName());
        this.companyName = clean(request.companyName());
        this.contactPerson = clean(request.contactPerson());
        this.email = clean(request.email());
        this.contactNumber = clean(request.contactNumber());
        this.address = clean(request.address());
        this.businessDomain = clean(request.businessDomain());
        this.onboardingDate = request.onboardingDate();
        this.remarks = clean(request.remarks());
    }

    /** Deactivate never deletes the client, its projects, tickets or history (FR-021). */
    public void activate() {
        this.status = "ACTIVE";
    }

    public void deactivate() {
        this.status = "INACTIVE";
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getClientName() { return clientName; }
    public String getCompanyName() { return companyName; }
    public String getContactPerson() { return contactPerson; }
    public String getEmail() { return email; }
    public String getContactNumber() { return contactNumber; }
    public String getAddress() { return address; }
    public String getBusinessDomain() { return businessDomain; }
    public String getStatus() { return status; }
    public LocalDate getOnboardingDate() { return onboardingDate; }
    public String getRemarks() { return remarks; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Integer getVersion() { return version; }
}