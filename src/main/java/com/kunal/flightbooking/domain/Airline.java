package com.kunal.flightbooking.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "airlines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Airline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 3)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    public Airline(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
