package org.example.synchro.repositories;

import org.example.synchro.entities.DailyPopularSelection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface DailyPopularSelectionRepository extends JpaRepository<DailyPopularSelection, LocalDate> {}
