package uz.mirmaxsudov.chatclonebackend.repository.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.Message;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
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
