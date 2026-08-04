package uz.mirmaxsudov.chatclonebackend.config.hibernate;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uz.mirmaxsudov.chatclonebackend.listener.SoftDeleteEventListener;

@Configuration
public class HibernateConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer(
            SoftDeleteEventListener listener
    ) {
        return properties ->
                properties.put("hibernate.ejb.event.delete", listener);
    }
}