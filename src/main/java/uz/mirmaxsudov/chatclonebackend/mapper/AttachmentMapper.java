package uz.mirmaxsudov.chatclonebackend.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.MessageAttachment;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.AttachmentType;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.message.AttachmentMessageResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.message.MessageAttachmentResponse;
import uz.mirmaxsudov.chatclonebackend.service.attachment.AttachmentService;
import uz.mirmaxsudov.chatclonebackend.service.attachment.AttachmentPublicURLResolver;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AttachmentMapper {
    private final AttachmentPublicURLResolver attachmentPublicURLResolver;

    public List<MessageAttachmentResponse> toMessageAttachmentResponses(List<MessageAttachment> attachments) {
        return attachments.stream().map(this::toMessageAttachmentResponse).toList();
    }

    private MessageAttachmentResponse toMessageAttachmentResponse(MessageAttachment messageAttachment) {
        Attachment attachment = messageAttachment.getAttachment();

        return new MessageAttachmentResponse(
                messageAttachment.getSortOrder(),
                attachment == null ? null :
                        new AttachmentMessageResponse(
                                attachment.getOriginalFileName(),
                                attachment.getContentType(),
                                attachment.getSizeBytes(),
                                attachmentPublicURLResolver.resolvePublicURL(attachment.getId()),
                                attachment.getType(),
                                thumbnailURL(attachment)
                        )
        );
    }

    private String thumbnailURL(Attachment attachment) {
        if (attachment.getType() == null || attachment.getMetadata() == null)
            return null;

        String thumbnailStorageKey = attachment.getMetadata()
                .get(AttachmentService.THUMBNAIL_STORAGE_KEY_METADATA);
        return attachment.getType() == AttachmentType.VIDEO
                && thumbnailStorageKey != null
                && !thumbnailStorageKey.isBlank()
                ? attachmentPublicURLResolver.resolveThumbnailURL(attachment.getId())
                : null;
    }
}
