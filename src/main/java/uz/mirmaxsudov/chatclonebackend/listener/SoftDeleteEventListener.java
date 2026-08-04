package uz.mirmaxsudov.chatclonebackend.listener;

import org.hibernate.event.spi.DeleteContext;
import org.hibernate.event.spi.DeleteEvent;
import org.hibernate.event.spi.DeleteEventListener;
import org.springframework.stereotype.Component;
import uz.mirmaxsudov.chatclonebackend.model.entity.base.BaseEntity;

import java.time.LocalDateTime;

@Component
public class SoftDeleteEventListener implements DeleteEventListener {

    @Override
    public void onDelete(DeleteEvent deleteEvent) {
        var entity = deleteEvent.getObject();
        if (entity instanceof BaseEntity base) {
            base.setDeleted(true);
            base.setDeletedAt(LocalDateTime.now());
            deleteEvent.getSession().merge(base);
            deleteEvent.getSession().flush();
        }
    }

    @Override
    public void onDelete(DeleteEvent deleteEvent, DeleteContext deleteContext) {
        onDelete(deleteEvent);
    }
}