package uz.mirmaxsudov.chatclonebackend.service.impl.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.Message;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.MessageAttachment;
import uz.mirmaxsudov.chatclonebackend.model.request.chat.SendMessageAttachmentRequest;
import uz.mirmaxsudov.chatclonebackend.repository.chat.message.attachment.MessageAttachmentRepository;
import uz.mirmaxsudov.chatclonebackend.exceptions.CustomBadRequestException;
import uz.mirmaxsudov.chatclonebackend.service.base.AttachmentService;
import uz.mirmaxsudov.chatclonebackend.service.base.chat.MessageAttachmentService;

import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

@Service
@RequiredArgsConstructor
public class MessageAttachmentServiceImpl implements MessageAttachmentService {
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final AttachmentService attachmentService;

    @Override
    public List<MessageAttachment> createAttachments(
            Message message,
            List<SendMessageAttachmentRequest> attachmentsRequests,
            UUID currentUserId
    ) {
        if (attachmentsRequests == null || attachmentsRequests.isEmpty())
            return Collections.emptyList();

        Map<UUID, SendMessageAttachmentRequest> requestsById;

        try {
            requestsById = attachmentsRequests .stream().collect(toMap(
                    SendMessageAttachmentRequest::id,
                    Function.identity()
            ));
        } catch (IllegalStateException exception) {
            throw new CustomBadRequestException("An attachment cannot be included more than once");
        }

        if (requestsById.values().stream().map(SendMessageAttachmentRequest::sortOrder).distinct().count()
                != requestsById.size())
            throw new CustomBadRequestException("Attachment sort orders must be unique");

        List<Attachment> attachments = attachmentService.getAllByIds(requestsById.keySet(), currentUserId);

        if (attachments.size() != requestsById.size())
            throw new CustomBadRequestException("One or more attachments are invalid");

        List<MessageAttachment> messageAttachments = new ArrayList<>();

        for (Attachment attachment : attachments) {
            messageAttachments.add(MessageAttachment.builder()
                    .attachment(attachment)
                    .message(message)
                    .sortOrder(requestsById.get(attachment.getId()).sortOrder())
                    .build());
        }

        messageAttachments.sort(Comparator.comparingInt(MessageAttachment::getSortOrder));
        return messageAttachmentRepository.saveAllAndFlush(messageAttachments);
    }

    @Override
    public Map<UUID, List<MessageAttachment>> getAttachmentsByMessageIds(Collection<UUID> messageIds) {
        if (messageIds == null || messageIds.isEmpty())
            return Collections.emptyMap();

        return messageAttachmentRepository.findAllByMessageIds(messageIds).stream()
                .collect(groupingBy(
                        attachment -> attachment.getMessage().getId(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
    }
}
