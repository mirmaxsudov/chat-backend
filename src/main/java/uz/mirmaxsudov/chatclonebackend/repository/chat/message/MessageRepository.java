package uz.mirmaxsudov.chatclonebackend.repository.chat.message;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.Message;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m from Message m
            join fetch m.sender
            join fetch m.chat
            where m.id = :messageId
              and m.chat.id = :chatId
              and m.deleted = false
            """)
    Optional<Message> findByIdAndChatIdAndDeletedFalseForUpdate(
            @Param("messageId") UUID messageId,
            @Param("chatId") UUID chatId
    );

    Slice<Message> findByChatIdAndDeletedFalseAndSeqLessThanOrderBySeqDesc(
            UUID chatId,
            long beforeSeq,
            Pageable pageable
    );

    @Query("""
            select m from Message m
            join fetch m.sender
            where m.chat.id in :chatIds
              and m.deleted = false
              and m.seq = (
                  select max(m2.seq) from Message m2
                  where m2.chat = m.chat and m2.deleted = false
              )
            """)
    List<Message> findLatestByChatIds(@Param("chatIds") Collection<UUID> chatIds);
}
