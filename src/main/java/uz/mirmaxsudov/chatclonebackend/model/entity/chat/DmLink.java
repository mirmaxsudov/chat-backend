package uz.mirmaxsudov.chatclonebackend.model.entity.chat;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.*;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.model.entity.base.BaseEntity;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DmLink extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER)
    private Chat chat;
    @ManyToOne(fetch = FetchType.EAGER)
    private User firstUser;
    @ManyToOne(fetch = FetchType.EAGER)
    private User secondUser;
}