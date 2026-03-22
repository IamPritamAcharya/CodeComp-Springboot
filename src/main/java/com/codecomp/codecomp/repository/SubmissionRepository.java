package com.codecomp.codecomp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codecomp.codecomp.models.Submission;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {
}