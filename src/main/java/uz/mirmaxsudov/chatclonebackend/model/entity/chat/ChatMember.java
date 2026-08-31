package uz.mirmaxsudov.chatclonebackend.model.entity.chat;

import jakarta.persistence.*;
import lombok.*;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.entity.base.BaseEntity;
import uz.mirmaxsudov.chatclonebackend.model.enums.chat.MemberRole;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_chat_member_chat_user", columnNames = {"chat_id", "user_id"})
}, indexes = {
        @Index(name = "idx_chat_member_user", columnList = "user_id"),
        @Index(name = "idx_chat_member_chat", columnList = "chat_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMember extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();
}
