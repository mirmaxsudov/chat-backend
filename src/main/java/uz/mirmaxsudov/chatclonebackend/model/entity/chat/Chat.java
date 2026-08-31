package uz.mirmaxsudov.chatclonebackend.model.entity.chat;

import jakarta.persistence.*;
import lombok.*;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.entity.base.BaseEntity;
import uz.mirmaxsudov.chatclonebackend.model.enums.chat.ChatType;

@Entity
@Table(name = "chats", indexes = {
        @Index(name = "idx_chat_updated_at", columnList = "updated_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Chat extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatType type;

    @Column(nullable = false)
    private long lastMessageSeq;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;
}
