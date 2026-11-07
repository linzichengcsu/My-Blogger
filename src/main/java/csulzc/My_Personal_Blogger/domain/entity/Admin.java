package csulzc.My_Personal_Blogger.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Entity
@Table(name = "admins")
public class Admin extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, length = 60)
    private String passwordHash;

    @Transient
    private String plainPassword;

    @Column(nullable = false, unique = true)
    private String email;

    private String displayName;

    private String avatar;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AdminRole role = AdminRole.SUPER_ADMIN;

    @Builder.Default
    private Boolean isActive = true;

    private Instant lastLoginAt;

    private String bio;

    public enum AdminRole {
        SUPER_ADMIN, CONTENT_ADMIN, USER_ADMIN
    }

    protected void onCreate() {
        super.onCreate();
        if (isActive == null) {
            isActive = true;
        }
    }
}
