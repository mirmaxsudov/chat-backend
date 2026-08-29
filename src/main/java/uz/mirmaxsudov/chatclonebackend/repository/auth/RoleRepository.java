package uz.mirmaxsudov.chatclonebackend.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.Role;
import uz.mirmaxsudov.chatclonebackend.model.enums.auth.RoleName;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(RoleName name);
}
