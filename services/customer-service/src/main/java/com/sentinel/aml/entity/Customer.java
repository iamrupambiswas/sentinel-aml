package com.sentinel.aml.entity;

import com.sentinel.aml.converter.PIIAttributeConverter;
import com.sentinel.aml.enums.CustomerType;
import com.sentinel.aml.enums.RiskRating;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(nullable = false, length = 20)
    private CustomerType type;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    // Automatically encrypted/decrypted by our converter
    @Convert(converter = PIIAttributeConverter.class)
    @Column(name = "id_number", nullable = false)
    private String idNumber;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(nullable = false, length = 3)
    private String nationality;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(name = "risk_rating", nullable = false, length = 20)
    private RiskRating riskRating;

    @Column(name = "onboarded_at", nullable = false)
    private LocalDateTime onboardedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Account> accounts;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}