package uz.mirmaxsudov.chatclonebackend.repository.queryDsl.user.impl;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.QUser;
import uz.mirmaxsudov.chatclonebackend.model.entity.auth.User;
import uz.mirmaxsudov.chatclonebackend.repository.queryDsl.user.base.UserQueryRepository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserQueryRepositoryImpl implements UserQueryRepository {
    private final JPAQueryFactory jpaQueryFactory;
    private final static QUser user = QUser.user;

    @Override
    public List<User> searchByUsernameOrName(String keyword) {
        return jpaQueryFactory.selectFrom(user)
                .where(
                        user.username.containsIgnoreCase(keyword)
                                .or(user.firstname.containsIgnoreCase(keyword))
                                .or(user.lastname.containsIgnoreCase(keyword))
                ).orderBy(user.username.asc())
                .fetch();
    }
}
