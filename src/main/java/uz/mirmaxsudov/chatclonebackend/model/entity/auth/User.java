package uz.mirmaxsudov.chatclonebackend.model.entity.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import uz.mirmaxsudov.chatclonebackend.model.entity.base.BaseEntity;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String phoneNumber;
    @Column(unique = true)
    private String username;
    private String firstname;
    private String lastname;
    private String passwordHash;
}