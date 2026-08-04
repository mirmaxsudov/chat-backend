package uz.mirmaxsudov.chatclonebackend.config.hibernate;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

@Component
public class HibernateFilterEnabler {
    @PersistenceContext
    private EntityManager entityManager;

    @PostConstruct
    public void enableFilter() {
        entityManager
                .unwrap(Session.class)
                .enableFilter("softDeleteFilter");
    }
}