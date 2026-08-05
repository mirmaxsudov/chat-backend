package uz.mirmaxsudov.chatclonebackend.model.entity.chat;

import jakarta.persistence.*;
import lombok.*;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.entity.base.BaseEntity;
import uz.mirmaxsudov.chatclonebackend.model.enums.chat.MemberRole;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMember extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    private Chat chat;
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;
    @Enumerated(EnumType.STRING)
    private MemberRole role;
    @Column(nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();
}