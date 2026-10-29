package org.example.synchro.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class LastFmImportState {
    @Id
    private String username;
    private long fromTimestamp;
    private long toTimestamp;
    private int nextPage = 1;
    private long imported;
    private boolean complete;
}
