package uz.mirmaxsudov.chatclonebackend.repository.chat;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.Chat;

import java.util.Optional;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<Chat, UUID> {
    @Query("""
            select c from Chat c
            join ChatMember cm on cm.chat = c
            where cm.user.id = :userId
              and cm.deleted = false
              and c.deleted = false
              and c.type in (
                  uz.mirmaxsudov.chatclonebackend.model.enums.chat.ChatType.DIRECT,
                  uz.mirmaxsudov.chatclonebackend.model.enums.chat.ChatType.SAVED
              )
            order by c.updatedAt desc, c.id desc
            """)
    Page<Chat> findChatsByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            select c from Chat c
            join ChatMember cm on cm.chat = c
            where c.id = :chatId
              and cm.user.id = :userId
              and cm.deleted = false
              and c.deleted = false
              and c.type in (
                  uz.mirmaxsudov.chatclonebackend.model.enums.chat.ChatType.DIRECT,
                  uz.mirmaxsudov.chatclonebackend.model.enums.chat.ChatType.SAVED
              )
            """)
    Optional<Chat> findChatForUser(@Param("chatId") UUID chatId, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Chat c where c.id = :chatId and c.deleted = false")
    Optional<Chat> findByIdForUpdate(@Param("chatId") UUID chatId);
}
