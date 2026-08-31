package uz.mirmaxsudov.chatclonebackend.model.entity.chat;

import jakarta.persistence.*;
import lombok.*;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.entity.base.BaseEntity;

@Entity
@Table(name = "dm_links", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dm_link_users", columnNames = {"first_user_id", "second_user_id"}),
        @UniqueConstraint(name = "uk_dm_link_chat", columnNames = "chat_id")
}, indexes = {
        @Index(name = "idx_dm_link_first_user", columnList = "first_user_id"),
        @Index(name = "idx_dm_link_second_user", columnList = "second_user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DmLink extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false, unique = true)
    private Chat chat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "first_user_id", nullable = false)
    private User firstUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "second_user_id", nullable = false)
    private User secondUser;
}
