package com.bcsystems.bonds.domain;

import com.bcsystems.bonds.domain.en.TokenType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

@Audited
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "token")
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idToken;

    @Column(unique = true, length = 64)
    private String tokenHash;

    @Transient
    private String token;

    @Column(unique = true, length = 64)
    private String refreshTokenHash;

    @Transient
    private String refreshToken;

    @Column(length = 36)
    private String sessionFamily;

    private Boolean isRevoked;
    private Boolean isExpired;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TokenType type = TokenType.BEARER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona")
    private Persona persona;
}
