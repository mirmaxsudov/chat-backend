package uz.mirmaxsudov.chatclonebackend.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uz.mirmaxsudov.chatclonebackend.model.entity.attachment.Attachment;
import uz.mirmaxsudov.chatclonebackend.model.entity.chat.message.MessageAttachment;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.AttachmentType;
import uz.mirmaxsudov.chatclonebackend.model.enums.attachment.PreviewStatus;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.message.AttachmentMessageResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.message.AttachmentPreviewResponse;
import uz.mirmaxsudov.chatclonebackend.model.response.chat.message.MessageAttachmentResponse;
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
                                previewURL(attachment),
                                preview(attachment)
                        )
        );
    }

    private String previewURL(Attachment attachment) {
        return attachment.getPreviewStatus() == PreviewStatus.READY
                ? attachmentPublicURLResolver.resolvePreviewURL(attachment.getId())
                : null;
    }

    private AttachmentPreviewResponse preview(Attachment attachment) {
        if (attachment.getType() != AttachmentType.IMAGE && attachment.getType() != AttachmentType.VIDEO)
            return null;

        return new AttachmentPreviewResponse(
                attachment.getPreviewStatus() == null ? PreviewStatus.PENDING : attachment.getPreviewStatus(),
                previewURL(attachment),
                attachment.getPreviewContentType(),
                attachment.getPreviewSizeBytes(),
                attachment.getPreviewWidth(),
                attachment.getPreviewHeight()
        );
    }
}
