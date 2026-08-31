package uz.mirmaxsudov.chatclonebackend.repository.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.DmLink;

import java.util.Optional;
import java.util.UUID;

public interface DmLinkRepository extends JpaRepository<DmLink, UUID> {
    @Query("""
            select dl from DmLink dl
            join fetch dl.chat c
            join fetch dl.firstUser
            join fetch dl.secondUser
            where dl.firstUser.id = :firstUserId
              and dl.secondUser.id = :secondUserId
              and dl.deleted = false
              and c.deleted = false
            """)
    Optional<DmLink> findActiveByUsers(
            @Param("firstUserId") UUID firstUserId,
            @Param("secondUserId") UUID secondUserId
    );
}
