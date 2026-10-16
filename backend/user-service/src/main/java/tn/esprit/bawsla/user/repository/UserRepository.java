package tn.esprit.bawsla.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    long countByRoleAndActifTrue(Role role);

    List<User> findAllByOrderByDateCreationDesc();
}
