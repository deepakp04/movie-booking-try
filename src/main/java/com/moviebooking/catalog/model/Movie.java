package com.moviebooking.catalog.model;

import com.moviebooking.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.LinkedHashSet;
import java.util.Set;

import java.time.LocalDate;

@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movie extends BaseEntity {
    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String castMembers; // e.g., "Actor A, Actor B"

    @Column(length = 150)
    private String director;

    @Column(nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CbfcRating cbfcRating;

    private String posterUrl;
    private String bannerUrl;

    @Column(nullable = false)
    private LocalDate releaseDate;

    @ElementCollection
    @CollectionTable(name = "movie_languages", joinColumns = @JoinColumn(name = "movie_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 30)
    @Builder.Default
    private Set<AudioLanguage> availableLanguages = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "movie_formats", joinColumns = @JoinColumn(name = "movie_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "format", length = 30)
    @Builder.Default
    private Set<MovieFormat> availableFormats = new LinkedHashSet<>();
}
