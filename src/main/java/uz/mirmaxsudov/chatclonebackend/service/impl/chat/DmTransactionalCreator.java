package uz.mirmaxsudov.chatclonebackend.service.impl.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.mirmaxsudov.chatclonebackend.exceptions.CustomNotFoundException;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.Chat;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.ChatMember;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.DmLink;
import uz.mirmaxsudov.chatclonebackend.model.enums.chat.ChatType;
import uz.mirmaxsudov.chatclonebackend.model.enums.chat.MemberRole;
import uz.mirmaxsudov.chatclonebackend.repository.chat.ChatMemberRepository;
import uz.mirmaxsudov.chatclonebackend.repository.chat.ChatRepository;
import uz.mirmaxsudov.chatclonebackend.repository.chat.DmLinkRepository;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DmTransactionalCreator {
    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final DmLinkRepository dmLinkRepository;

    @Transactional
    public UUID create(UUID firstUserId, UUID secondUserId, UUID initiatorId) {
        User firstUser = findUser(firstUserId);
        User secondUser = findUser(secondUserId);

        User initiator = initiatorId.equals(firstUserId) ? firstUser : secondUser;

        Chat chat = chatRepository.save(Chat.builder()
                .type(ChatType.DIRECT)
                .lastMessageSeq(0)
                .owner(initiator)
                .build());

        chatMemberRepository.saveAll(List.of(
                member(chat, firstUser),
                member(chat, secondUser)
        ));

        dmLinkRepository.saveAndFlush(DmLink.builder()
                .chat(chat)
                .firstUser(firstUser)
                .secondUser(secondUser)
                .build());

        return chat.getId();
    }

    private User findUser(UUID userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomNotFoundException("User not found"));
    }

    private ChatMember member(Chat chat, User user) {
        return ChatMember.builder()
                .chat(chat)
                .user(user)
                .role(MemberRole.MEMBER)
                .build();
    }
}
