package com.capstone.eval.repository;

import com.capstone.eval.model.User;
import com.capstone.eval.model.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRoleAndAcademicYearAndSemester(Role role, String academicYear, String semester);
}
