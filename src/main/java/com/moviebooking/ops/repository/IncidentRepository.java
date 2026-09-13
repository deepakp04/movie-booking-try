package com.moviebooking.ops.repository;

import com.moviebooking.ops.model.Incident;
import com.moviebooking.ops.model.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    @Query("SELECT i FROM Incident i WHERE i.isDeleted = false ORDER BY i.createdAt DESC")
    List<Incident> findAllActive();

    @Query("SELECT i FROM Incident i WHERE i.theatre.id = :theatreId AND i.isDeleted = false ORDER BY i.createdAt DESC")
    List<Incident> findByTheatreId(@Param("theatreId") Long theatreId);

    Optional<Incident> findByIdAndIsDeletedFalse(Long id);

    @Query("SELECT i FROM Incident i WHERE i.theatre.id = :theatreId AND i.isDeleted = false AND i.status IN :statuses ORDER BY i.createdAt DESC")
    List<Incident> findByTheatreIdAndStatusIn(
        @Param("theatreId") Long theatreId,
        @Param("statuses") List<IncidentStatus> statuses
    );

    long countByTheatreIdAndIsDeletedFalse(Long theatreId);

    long countByTheatreIdAndIsDeletedFalseAndStatusIn(Long theatreId, List<IncidentStatus> statuses);
}
