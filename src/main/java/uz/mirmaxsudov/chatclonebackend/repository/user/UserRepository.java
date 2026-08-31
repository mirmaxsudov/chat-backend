package uz.mirmaxsudov.chatclonebackend.repository.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhoneNumberAndDeletedFalse(String phoneNumber);

    Optional<User> findByIdAndDeletedFalse(UUID id);

    Optional<User> findByUsernameIgnoreCaseAndDeletedFalse(String username);

    @Query("""
            select u from User u
            where u.deleted = false
              and u.id <> :excludedUserId
              and u.username is not null
              and lower(u.username) like lower(concat('%', :keyword, '%'))
            order by u.username asc, u.id asc
            """)
    Page<User> searchByUsername(
            @Param("keyword") String keyword,
            @Param("excludedUserId") UUID excludedUserId,
            Pageable pageable
    );

    boolean existsByPhoneNumberOrUsername(String phoneNumber, String username);
}
