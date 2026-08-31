package uz.mirmaxsudov.chatclonebackend.repository.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.SavedChatLink;

import java.util.Optional;
import java.util.UUID;

public interface SavedChatLinkRepository extends JpaRepository<SavedChatLink, UUID> {
    @Query("""
            select sl from SavedChatLink sl
            join fetch sl.chat c
            join fetch sl.user u
            where u.id = :userId
              and sl.deleted = false
              and c.deleted = false
              and u.deleted = false
            """)
    Optional<SavedChatLink> findActiveByUserId(@Param("userId") UUID userId);
}
