package com.codecomp.codecomp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codecomp.codecomp.models.Problem;

public interface ProblemRepository extends JpaRepository<Problem, Long> {
}
