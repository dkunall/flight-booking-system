package com.kunal.flightbooking.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "gateway_reference", length = 64)
    private String gatewayReference;

    @Column(name = "failure_reason")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Payment(Booking booking, PaymentMethod method, BigDecimal amount) {
        this.booking = booking;
        this.method = method;
        this.amount = amount;
    }

    public void markSuccess(String gatewayReference) {
        requireStatus(PaymentStatus.INITIATED);
        this.status = PaymentStatus.SUCCESS;
        this.gatewayReference = gatewayReference;
    }

    public void markFailed(String reason) {
        requireStatus(PaymentStatus.INITIATED);
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public void markRefunded() {
        requireStatus(PaymentStatus.SUCCESS);
        this.status = PaymentStatus.REFUNDED;
    }

    private void requireStatus(PaymentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Payment " + id + " is " + status + ", expected " + expected);
        }
    }
}
