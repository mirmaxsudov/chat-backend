package uz.mirmaxsudov.chatclonebackend.service.impl.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.mirmaxsudov.chatclonebackend.event.chat.MessageCreatedEvent;
import uz.mirmaxsudov.chatclonebackend.exceptions.CustomBadRequestException;
import uz.mirmaxsudov.chatclonebackend.exceptions.CustomNotFoundException;
import uz.mirmaxsudov.chatclonebackend.mapper.AttachmentMapper;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.Chat;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.ChatMember;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.DmLink;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.SavedChatLink;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.Message;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.MessageAttachment;
import uz.mirmaxsudov.chatclonebackend.model.enums.chat.ChatType;
import uz.mirmaxsudov.chatclonebackend.model.request.chat.SendMessageRequest;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.ChatResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.MessageHistoryResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.MessageResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.user.PublicUserResponse;
import uz.mirmaxsudov.chatclonebackend.repository.chat.ChatMemberRepository;
import uz.mirmaxsudov.chatclonebackend.repository.chat.ChatRepository;
import uz.mirmaxsudov.chatclonebackend.repository.chat.dm.DmLinkRepository;
import uz.mirmaxsudov.chatclonebackend.repository.chat.dm.SavedChatLinkRepository;
import uz.mirmaxsudov.chatclonebackend.repository.chat.message.MessageRepository;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;
import uz.mirmaxsudov.chatclonebackend.service.base.chat.ChatService;
import uz.mirmaxsudov.chatclonebackend.service.base.chat.MessageAttachmentService;
import uz.mirmaxsudov.chatclonebackend.service.impl.chat.helper.DmTransactionalCreator;
import uz.mirmaxsudov.chatclonebackend.service.impl.chat.helper.SavedChatTransactionalCreator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
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
    private final MessageAttachmentService messageAttachmentService;

    // Mappers
    private final AttachmentMapper attachmentMapper;

    @Override
    public ChatResponse createOrGetDm(UUID currentUserId, String username) {
        User currentUser = findUser(currentUserId);
        User targetUser = userRepository.findByUsernameIgnoreCaseAndDeletedFalse(normalizeUsername(username))
                .orElseThrow(() -> new CustomNotFoundException("User not found"));

        if (currentUser.getId().equals(targetUser.getId()))
            throw new CustomBadRequestException("A direct chat cannot be created with yourself");

        UserPair pair = canonicalPair(currentUser, targetUser);
        DmLink existing = dmLinkRepository.findActiveByUsers(pair.first().getId(), pair.second().getId())
                .orElse(null);

        if (existing != null) {
            log.debug(
                    "Returning existing direct chat: chatId={}, requesterId={}",
                    existing.getChat().getId(),
                    currentUserId
            );
            return getChat(currentUserId, existing.getChat().getId());
        }


        UUID chatId;
        try {
            chatId = dmTransactionalCreator.create(
                    pair.first().getId(),
                    pair.second().getId(),
                    currentUserId
            );
        } catch (DataIntegrityViolationException exception) {
            log.warn(
                    "Direct chat creation race detected; loading winner: requesterId={}, targetUserId={}",
                    currentUserId,
                    targetUser.getId()
            );
            chatId = dmLinkRepository.findActiveByUsers(pair.first().getId(), pair.second().getId())
                    .map(link -> link.getChat().getId())
                    .orElseThrow(() -> exception);
        }

        log.info(
                "Direct chat ready: chatId={}, requesterId={}, targetUserId={}",
                chatId,
                currentUserId,
                targetUser.getId()
        );

        return getChat(currentUserId, chatId);
    }

    @Override
    public ChatResponse createOrGetSavedChat(UUID currentUserId) {
        SavedChatLink existing = savedChatLinkRepository.findActiveByUserId(currentUserId)
                .orElse(null);
        if (existing != null) {
            log.debug(
                    "Returning existing saved chat: chatId={}, userId={}",
                    existing.getChat().getId(),
                    currentUserId
            );
            return getChat(currentUserId, existing.getChat().getId());
        }

        UUID chatId;
        try {
            chatId = savedChatTransactionalCreator.create(currentUserId);
        } catch (DataIntegrityViolationException exception) {
            log.warn("Saved chat creation race detected; loading winner: userId={}", currentUserId);
            chatId = savedChatLinkRepository.findActiveByUserId(currentUserId)
                    .map(link -> link.getChat().getId())
                    .orElseThrow(() -> exception);
        }

        log.info("Saved chat ready: chatId={}, userId={}", chatId, currentUserId);

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
            log.debug("Chat list is empty: userId={}, page={}, size={}", currentUserId, page, size);
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
        Map<UUID, List<MessageAttachment>> attachmentsByMessageId = attachmentsByMessageId(
                latestMessages.values()
        );

        Page<ChatResponse> results = chats.map(chat -> toChatResponse(
                chat,
                chat.getType() == ChatType.SAVED
                        ? currentUser
                        : requirePeer(peers.get(chat.getId())),
                latestMessages.get(chat.getId()),
                currentUserId,
                attachmentsByMessageId
        ));

        log.debug(
                "Chat list loaded: userId={}, page={}, size={}, resultCount={}, hasNext={}",
                currentUserId,
                page,
                size,
                results.getNumberOfElements(),
                results.hasNext()
        );
        return results;
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

        Map<UUID, List<MessageAttachment>> attachmentsByMessageId = latestMessage == null
                ? Map.of()
                : attachmentsByMessageId(List.of(latestMessage));

        return toChatResponse(chat, peer, latestMessage, currentUserId, attachmentsByMessageId);
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

        if (cursor < 1)
            throw new CustomBadRequestException("beforeSeq must be greater than zero");


        var messages = messageRepository.findByChatIdAndDeletedFalseAndSeqLessThanOrderBySeqDesc(
                chatId,
                cursor,
                PageRequest.of(0, size)
        );

        Map<UUID, List<MessageAttachment>> attachmentsByMessageId = attachmentsByMessageId(
                messages.getContent()
        );
        List<MessageResponse> results = messages.getContent().stream()
                .map(message -> toMessageResponse(
                        message,
                        currentUserId,
                        attachmentsByMessageId.getOrDefault(message.getId(), List.of())
                ))
                .toList();

        Long nextCursor = messages.hasNext() && !results.isEmpty()
                ? results.getLast().seq()
                : null;

        log.debug(
                "Message history loaded: chatId={}, userId={}, beforeSeq={}, resultCount={}, hasNext={}",
                chatId,
                currentUserId,
                beforeSeq,
                results.size(),
                messages.hasNext()
        );
        return new MessageHistoryResponse(results, nextCursor, messages.hasNext());
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(UUID currentUserId, UUID chatId, SendMessageRequest request) {
        Chat chat = chatRepository.findByIdForUpdate(chatId)
                .filter(candidate -> chatMemberRepository
                        .existsByChatIdAndUserIdAndDeletedFalse(candidate.getId(), currentUserId))
                .filter(candidate -> candidate.getType() == ChatType.DIRECT
                        || candidate.getType() == ChatType.SAVED)
                .orElseThrow(() -> new CustomNotFoundException("Chat not found"));

        User sender = findUser(currentUserId);

        long nextSequence = chat.getLastMessageSeq() + 1;
        chat.setLastMessageSeq(nextSequence);

        // We create message
        Message message = messageRepository.saveAndFlush(Message.builder()
                .chat(chat)
                .seq(nextSequence)
                .sender(sender)
                .text(request.text().strip())
                .build());

        // Now, we will create attachments for the message
        List<MessageAttachment> attachments = messageAttachmentService.createAttachments(message, request.attachments(), currentUserId);

        List<UUID> recipientIds = chatMemberRepository.findActiveUserIdsByChatId(chatId);

        eventPublisher.publishEvent(new MessageCreatedEvent(
                chat.getId(),
                chat.getType(),
                message.getId(),
                message.getSeq(),
                sender.getId(),
                message.getText(),
                message.getCreatedAt(),
                recipientIds,
                attachmentMapper.toMessageAttachmentResponses(attachments)
        ));

        log.info(
                "Message created: messageId={}, chatId={}, seq={}, senderId={}, recipientCount={}, attachments-count={}",
                message.getId(),
                chatId,
                message.getSeq(),
                currentUserId,
                recipientIds.size(),
                attachments.size()
        );

        return toMessageResponse(message, currentUserId, attachments);
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

    private ChatResponse toChatResponse(
            Chat chat,
            User peer,
            Message latestMessage,
            UUID currentUserId,
            Map<UUID, List<MessageAttachment>> attachmentsByMessageId
    ) {
        return new ChatResponse(
                chat.getId(),
                chat.getType(),
                toPublicUserResponse(peer),
                latestMessage == null ? null : toMessageResponse(
                        latestMessage,
                        currentUserId,
                        attachmentsByMessageId.getOrDefault(latestMessage.getId(), List.of())
                ),
                chat.getCreatedAt(),
                chat.getUpdatedAt()
        );
    }

    private MessageResponse toMessageResponse(
            Message message,
            UUID currentUserId,
            List<MessageAttachment> attachments
    ) {
        return new MessageResponse(
                message.getId(),
                message.getSeq(),
                message.getSender().getId(),
                message.getText(),
                message.getCreatedAt(),
                message.getSender().getId().equals(currentUserId),
                attachmentMapper.toMessageAttachmentResponses(attachments)
        );
    }

    private Map<UUID, List<MessageAttachment>> attachmentsByMessageId(
            java.util.Collection<Message> messages
    ) {
        return messageAttachmentService.getAttachmentsByMessageIds(
                messages.stream().map(Message::getId).toList()
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
