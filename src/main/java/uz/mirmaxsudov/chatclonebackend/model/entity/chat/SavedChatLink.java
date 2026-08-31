package uz.mirmaxsudov.chatclonebackend.model.entity.chat;

import jakarta.persistence.*;
import lombok.*;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.entity.base.BaseEntity;

@Entity
@Table(name = "saved_chat_links", uniqueConstraints = {
        @UniqueConstraint(name = "uk_saved_chat_link_user", columnNames = "user_id"),
        @UniqueConstraint(name = "uk_saved_chat_link_chat", columnNames = "chat_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedChatLink extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false, unique = true)
    private Chat chat;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
}
