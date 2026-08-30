package com.moviebooking.catalog.repository;

import com.moviebooking.catalog.model.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findAllByOrderByNameAsc();

    List<City> findByIsDeletedFalseOrderByNameAsc();

    Optional<City> findByIdAndIsDeletedFalse(Long id);
}
