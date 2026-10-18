package com.tradepulse.tradepulse.backtest.entity;

import com.tradepulse.tradepulse.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "strategies")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Strategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> parameters;
}
