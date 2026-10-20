package ac.at.tgm.submit.user;

import ac.at.tgm.submit.security.Role;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByUsername(String username);

    boolean existsByIdAndRole(UUID id, Role role);

    Page<UserAccount> findAll(Pageable pageable);

    @Query("select user from UserAccount user "
            + "where (:role is null or user.role = :role) "
            + "and (:search is null or lower(user.username) like concat('%', :search, '%') escape '!' "
            + "or lower(user.displayName) like concat('%', :search, '%') escape '!') "
            + "order by user.id")
    Page<UserAccount> findByFilters(@Param("role") Role role, @Param("search") String search, Pageable pageable);

    @Query("select user from UserAccount user where user.role = :role "
            + "and (:search is null or lower(user.displayName) like concat('%', :search, '%') escape '!') "
            + "order by lower(user.displayName), user.id")
    Page<UserAccount> findStudentsByDisplayName(@Param("role") Role role, @Param("search") String search,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserAccount user where user.role = :role")
    List<UserAccount> findAllByRoleForUpdate(@Param("role") Role role);
}
