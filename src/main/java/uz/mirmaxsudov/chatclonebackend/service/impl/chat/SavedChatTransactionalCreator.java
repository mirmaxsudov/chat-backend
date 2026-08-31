package uz.mirmaxsudov.chatclonebackend.service.impl.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.mirmaxsudov.chatclonebackend.exceptions.CustomNotFoundException;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.Chat;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.ChatMember;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.SavedChatLink;
import uz.mirmaxsudov.chatclonebackend.model.enums.chat.ChatType;
import uz.mirmaxsudov.chatclonebackend.model.enums.chat.MemberRole;
import uz.mirmaxsudov.chatclonebackend.repository.chat.ChatMemberRepository;
import uz.mirmaxsudov.chatclonebackend.repository.chat.ChatRepository;
import uz.mirmaxsudov.chatclonebackend.repository.chat.SavedChatLinkRepository;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SavedChatTransactionalCreator {
    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final SavedChatLinkRepository savedChatLinkRepository;

    @Transactional
    public UUID create(UUID userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomNotFoundException("User not found"));

        Chat chat = chatRepository.save(Chat.builder()
                .type(ChatType.SAVED)
                .lastMessageSeq(0)
                .owner(user)
                .build());

        chatMemberRepository.save(ChatMember.builder()
                .chat(chat)
                .user(user)
                .role(MemberRole.OWNER)
                .build());

        savedChatLinkRepository.saveAndFlush(SavedChatLink.builder()
                .chat(chat)
                .user(user)
                .build());

        return chat.getId();
    }
}
