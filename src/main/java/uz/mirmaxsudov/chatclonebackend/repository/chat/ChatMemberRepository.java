package uz.mirmaxsudov.chatclonebackend.repository.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.ChatMember;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatMemberRepository extends JpaRepository<ChatMember, UUID> {
    boolean existsByChatIdAndUserIdAndDeletedFalse(UUID chatId, UUID userId);

    @EntityGraph(attributePaths = {"chat", "user"})
    List<ChatMember> findByChatIdInAndUserIdNotAndDeletedFalse(Collection<UUID> chatIds, UUID userId);

    @EntityGraph(attributePaths = {"chat", "user"})
    List<ChatMember> findByChatIdAndUserIdNotAndDeletedFalse(UUID chatId, UUID userId);

    @Query("""
            select cm.user.id from ChatMember cm
            where cm.chat.id = :chatId
              and cm.deleted = false
              and cm.user.deleted = false
            """)
    List<UUID> findActiveUserIdsByChatId(@Param("chatId") UUID chatId);
}
