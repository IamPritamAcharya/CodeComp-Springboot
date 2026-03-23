package com.codecomp.codecomp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codecomp.codecomp.models.TestCase;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByProblemId(Long problemId);

}
