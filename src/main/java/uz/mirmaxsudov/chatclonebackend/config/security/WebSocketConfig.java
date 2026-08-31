package uz.mirmaxsudov.chatclonebackend.config.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import uz.mirmaxsudov.chatclonebackend.security.websocket.WebSocketJwtChannelInterceptor;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final WebSocketJwtChannelInterceptor jwtChannelInterceptor;
    private final TaskScheduler heartbeatScheduler;
    private final List<String> allowedOrigins;

    public WebSocketConfig(
            WebSocketJwtChannelInterceptor jwtChannelInterceptor,
            @Qualifier("webSocketHeartbeatScheduler") TaskScheduler heartbeatScheduler,
            @Value("${app.security.cors.allowed-origins}") List<String> allowedOrigins
    ) {
        this.jwtChannelInterceptor = jwtChannelInterceptor;
        this.heartbeatScheduler = heartbeatScheduler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
                .addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[]{10_000, 10_000})
                .setTaskScheduler(heartbeatScheduler);
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setMessageSizeLimit(16 * 1024)
                .setSendBufferSizeLimit(64 * 1024)
                .setSendTimeLimit(15_000);
    }
}
