package uz.mirmaxsudov.chatclonebackend.service.impl.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.mirmaxsudov.chatclonebackend.exceptions.CustomBadRequestException;
import uz.mirmaxsudov.chatclonebackend.exceptions.CustomNotFoundException;
import uz.mirmaxsudov.chatclonebackend.event.chat.MessageCreatedEvent;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.Chat;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.ChatMember;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.DmLink;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.SavedChatLink;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.Message;
import uz.mirmaxsudov.chatclonebackend.model.enums.chat.ChatType;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.ChatResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.MessageHistoryResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.MessageResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.user.PublicUserResponse;
import uz.mirmaxsudov.chatclonebackend.repository.chat.ChatMemberRepository;
import uz.mirmaxsudov.chatclonebackend.repository.chat.ChatRepository;
import uz.mirmaxsudov.chatclonebackend.repository.chat.DmLinkRepository;
import uz.mirmaxsudov.chatclonebackend.repository.chat.MessageRepository;
import uz.mirmaxsudov.chatclonebackend.repository.chat.SavedChatLinkRepository;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;
import uz.mirmaxsudov.chatclonebackend.service.base.chat.ChatService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private static final long FIRST_PAGE_CURSOR = Long.MAX_VALUE;

    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final DmLinkRepository dmLinkRepository;
    private final MessageRepository messageRepository;
    private final SavedChatLinkRepository savedChatLinkRepository;
    private final DmTransactionalCreator dmTransactionalCreator;
    private final SavedChatTransactionalCreator savedChatTransactionalCreator;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public ChatResponse createOrGetDm(UUID currentUserId, String username) {
        User currentUser = findUser(currentUserId);
        User targetUser = userRepository.findByUsernameIgnoreCaseAndDeletedFalse(normalizeUsername(username))
                .orElseThrow(() -> new CustomNotFoundException("User not found"));

        if (currentUser.getId().equals(targetUser.getId())) {
            throw new CustomBadRequestException("A direct chat cannot be created with yourself");
        }

        UserPair pair = canonicalPair(currentUser, targetUser);
        DmLink existing = dmLinkRepository.findActiveByUsers(pair.first().getId(), pair.second().getId())
                .orElse(null);

        if (existing != null)
            return getChat(currentUserId, existing.getChat().getId());


        UUID chatId;
        try {
            chatId = dmTransactionalCreator.create(
                    pair.first().getId(),
                    pair.second().getId(),
                    currentUserId
            );
        } catch (DataIntegrityViolationException exception) {
            chatId = dmLinkRepository.findActiveByUsers(pair.first().getId(), pair.second().getId())
                    .map(link -> link.getChat().getId())
                    .orElseThrow(() -> exception);
        }

        return getChat(currentUserId, chatId);
    }

    @Override
    public ChatResponse createOrGetSavedChat(UUID currentUserId) {
        SavedChatLink existing = savedChatLinkRepository.findActiveByUserId(currentUserId)
                .orElse(null);
        if (existing != null) {
            return getChat(currentUserId, existing.getChat().getId());
        }

        UUID chatId;
        try {
            chatId = savedChatTransactionalCreator.create(currentUserId);
        } catch (DataIntegrityViolationException exception) {
            chatId = savedChatLinkRepository.findActiveByUserId(currentUserId)
                    .map(link -> link.getChat().getId())
                    .orElseThrow(() -> exception);
        }

        return getChat(currentUserId, chatId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatResponse> getChats(UUID currentUserId, int page, int size) {
        User currentUser = findUser(currentUserId);
        Page<Chat> chats = chatRepository.findChatsByUserId(
                currentUserId,
                PageRequest.of(page, size)
        );
        if (chats.isEmpty()) {
            return Page.empty(chats.getPageable());
        }

        List<UUID> chatIds = chats.stream().map(Chat::getId).toList();

        Map<UUID, User> peers = chatMemberRepository
                .findByChatIdInAndUserIdNotAndDeletedFalse(chatIds, currentUserId)
                .stream()
                .collect(Collectors.toMap(member -> member.getChat().getId(), ChatMember::getUser));

        Map<UUID, Message> latestMessages = messageRepository.findLatestByChatIds(chatIds)
                .stream()
                .collect(Collectors.toMap(message -> message.getChat().getId(), Function.identity()));

        return chats.map(chat -> toChatResponse(
                chat,
                chat.getType() == ChatType.SAVED
                        ? currentUser
                        : requirePeer(peers.get(chat.getId())),
                latestMessages.get(chat.getId()),
                currentUserId
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public ChatResponse getChat(UUID currentUserId, UUID chatId) {
        Chat chat = findAccessibleChat(chatId, currentUserId);
        User peer = chat.getType() == ChatType.SAVED
                ? findUser(currentUserId)
                : chatMemberRepository.findByChatIdAndUserIdNotAndDeletedFalse(chatId, currentUserId)
                        .stream()
                        .map(ChatMember::getUser)
                        .findFirst()
                        .orElseThrow(() -> new CustomNotFoundException("Chat not found"));
        Message latestMessage = messageRepository.findLatestByChatIds(List.of(chatId))
                .stream()
                .findFirst()
                .orElse(null);

        return toChatResponse(chat, peer, latestMessage, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public MessageHistoryResponse getMessages(
            UUID currentUserId,
            UUID chatId,
            Long beforeSeq,
            int size
    ) {
        findAccessibleChat(chatId, currentUserId);
        long cursor = beforeSeq == null ? FIRST_PAGE_CURSOR : beforeSeq;
        if (cursor < 1) {
            throw new CustomBadRequestException("beforeSeq must be greater than zero");
        }

        var messages = messageRepository.findByChatIdAndDeletedFalseAndSeqLessThanOrderBySeqDesc(
                chatId,
                cursor,
                PageRequest.of(0, size)
        );

        List<MessageResponse> results = messages.getContent().stream()
                .map(message -> toMessageResponse(message, currentUserId))
                .toList();

        Long nextCursor = messages.hasNext() && !results.isEmpty()
                ? results.getLast().seq()
                : null;

        return new MessageHistoryResponse(results, nextCursor, messages.hasNext());
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(UUID currentUserId, UUID chatId, String text) {
        Chat chat = chatRepository.findByIdForUpdate(chatId)
                .filter(candidate -> chatMemberRepository
                        .existsByChatIdAndUserIdAndDeletedFalse(candidate.getId(), currentUserId))
                .filter(candidate -> candidate.getType() == ChatType.DIRECT
                        || candidate.getType() == ChatType.SAVED)
                .orElseThrow(() -> new CustomNotFoundException("Chat not found"));

        User sender = findUser(currentUserId);

        long nextSequence = chat.getLastMessageSeq() + 1;
        chat.setLastMessageSeq(nextSequence);

        Message message = messageRepository.saveAndFlush(Message.builder()
                .chat(chat)
                .seq(nextSequence)
                .sender(sender)
                .text(text.strip())
                .build());

        List<UUID> recipientIds = chatMemberRepository.findActiveUserIdsByChatId(chatId);

        eventPublisher.publishEvent(new MessageCreatedEvent(
                chat.getId(),
                chat.getType(),
                message.getId(),
                message.getSeq(),
                sender.getId(),
                message.getText(),
                message.getCreatedAt(),
                recipientIds
        ));

        return toMessageResponse(message, currentUserId);
    }

    private Chat findAccessibleChat(UUID chatId, UUID userId) {
        return chatRepository.findChatForUser(chatId, userId)
                .orElseThrow(() -> new CustomNotFoundException("Chat not found"));
    }

    private User findUser(UUID userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomNotFoundException("User not found"));
    }

    private String normalizeUsername(String username) {
        String normalized = username == null ? "" : username.strip();
        return normalized.startsWith("@") ? normalized.substring(1) : normalized;
    }

    private UserPair canonicalPair(User first, User second) {
        return Comparator.comparing((User user) -> user.getId().toString())
                .compare(first, second) <= 0
                ? new UserPair(first, second)
                : new UserPair(second, first);
    }

    private User requirePeer(User peer) {
        if (peer == null) {
            throw new CustomNotFoundException("Chat peer not found");
        }
        return peer;
    }

    private ChatResponse toChatResponse(Chat chat, User peer, Message latestMessage, UUID currentUserId) {
        return new ChatResponse(
                chat.getId(),
                chat.getType(),
                toPublicUserResponse(peer),
                latestMessage == null ? null : toMessageResponse(latestMessage, currentUserId),
                chat.getCreatedAt(),
                chat.getUpdatedAt()
        );
    }

    private MessageResponse toMessageResponse(Message message, UUID currentUserId) {
        return new MessageResponse(
                message.getId(),
                message.getSeq(),
                message.getSender().getId(),
                message.getText(),
                message.getCreatedAt(),
                message.getSender().getId().equals(currentUserId)
        );
    }

    private PublicUserResponse toPublicUserResponse(User user) {
        return new PublicUserResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstname(),
                user.getLastname()
        );
    }

    private record UserPair(User first, User second) {
    }
}
