package org.example.synchro.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "daily_popular_selection")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyPopularSelection {
    @Id
    @Column(name = "selection_date")
    private LocalDate date;

    @Column(length = 120)
    private String artistIds;

    @Column(length = 120)
    private String albumIds;

    public DailyPopularSelection(LocalDate date) {
        this.date = date;
    }
}
