package uz.mirmaxsudov.chatclonebackend.model.entity.chat.message;

import jakarta.persistence.*;
import lombok.*;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.entity.base.BaseEntity;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.Chat;

@Entity
@Table(name = "messages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_message_chat_seq", columnNames = {"chat_id", "seq"})
}, indexes = {
        @Index(name = "idx_message_chat_seq", columnList = "chat_id, seq"),
        @Index(name = "idx_message_sender", columnList = "sender_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @Column(nullable = false)
    private long seq;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;
}
