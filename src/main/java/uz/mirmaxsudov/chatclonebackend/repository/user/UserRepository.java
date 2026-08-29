package uz.mirmaxsudov.chatclonebackend.repository.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.repository.queryDsl.user.base.UserQueryRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, UserQueryRepository {
    Optional<User> findByPhoneNumberAndDeletedFalse(String phoneNumber);

    Optional<User> findByIdAndDeletedFalse(UUID id);

    boolean existsByPhoneNumberOrUsername(String phoneNumber, String username);
}
