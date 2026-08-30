package com.moviebooking.catalog.repository;

import com.moviebooking.catalog.model.Theatre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TheatreRepository extends JpaRepository<Theatre, Long> {

    List<Theatre> findByCityId(Long cityId);

    Optional<Theatre> findByOwnerId(Long ownerId);

    List<Theatre> findByIsDeletedFalseOrderByNameAsc();

    List<Theatre> findByCityIdAndIsDeletedFalse(Long cityId);

    Optional<Theatre> findByIdAndIsDeletedFalse(Long id);

    Optional<Theatre> findByOwnerIdAndIsDeletedFalse(Long ownerId);

    boolean existsByCityIdAndIsDeletedFalse(Long cityId);
}
