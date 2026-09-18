package uz.mirmaxsudov.chatclonebackend.service.attachment;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@PropertySource("classpath:application-dev.yml")
public class AttachmentPublicURLResolver {
    @Value("${attachments.public-url-base:http://localhost:8080/api/v1/attachment}")
    private String publicURLBase;

    public String resolvePublicURL(UUID attachmentId) {
        return publicURLBase + "/" + attachmentId;
    }

    public String resolveThumbnailURL(UUID attachmentId) {
        return resolvePublicURL(attachmentId) + "/thumbnail";
    }
}
