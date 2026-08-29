package uz.mirmaxsudov.chatclonebackend.repository.queryDsl.user.base;

import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;

import java.util.List;

public interface UserQueryRepository {
    List<User> searchByUsername(String keyword);
}