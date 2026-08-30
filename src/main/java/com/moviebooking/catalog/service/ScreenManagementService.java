package com.moviebooking.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moviebooking.catalog.model.Screen;
import com.moviebooking.catalog.repository.ScreenRepository;
import com.moviebooking.catalog.repository.ShowRepository;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Single home for screen mutation logic, shared by AdminService and OwnerService.
 *
 * The only behavioural difference between the two callers is the
 * restrictToTheatreId parameter:
 *   null     -> admin, unrestricted across all theatres
 *   non-null -> theatre owner, may only touch screens in that theatre
 *
 * Keeping this in one place is what stops the two portals drifting apart the
 * way they had before (the owner portal never received the seat designer).
 */
@Service
@Transactional
public class ScreenManagementService {

    private final ScreenRepository screenRepository;
    private final ShowRepository showRepository;
    private final ObjectMapper objectMapper;

    public ScreenManagementService(ScreenRepository screenRepository,
                                   ShowRepository showRepository,
                                   ObjectMapper objectMapper) {
        this.screenRepository = screenRepository;
        this.showRepository = showRepository;
        this.objectMapper = objectMapper;
    }

    private Screen loadScreen(Long screenId, Long restrictToTheatreId) {
        Screen screen = screenRepository.findByIdAndIsDeletedFalse(screenId)
                .orElseThrow(() -> new ResourceNotFoundException("Screen not found with ID: " + screenId));

        if (restrictToTheatreId != null) {
            Long owning = (screen.getTheatre() != null) ? screen.getTheatre().getId() : null;
            if (!restrictToTheatreId.equals(owning)) {
                // Deliberately reported as "not found" rather than "forbidden" so an
                // owner cannot probe which screen IDs exist in other theatres.
                throw new ResourceNotFoundException("Screen not found with ID: " + screenId);
            }
        }
        return screen;
    }

    public Screen rename(Long screenId, Long restrictToTheatreId, String name, Integer totalSeats) {
        Screen screen = loadScreen(screenId, restrictToTheatreId);

        if (name != null && !name.isBlank()) {
            screen.setName(name.trim());
        }

        if (totalSeats != null) {
            if (totalSeats < 1) {
                throw new BusinessException("A screen must have at least one seat.");
            }
            // Capacity is derived from the layout once one exists; letting it be set
            // independently here would desync totalSeats from the seat matrix.
            if (screen.getLayoutJson() != null && !screen.getLayoutJson().isBlank()) {
                throw new BusinessException(
                        "This screen has a custom seat layout. Edit the layout instead - "
                      + "capacity is derived from it.");
            }
            screen.setTotalSeats(totalSeats);
        }
        return screenRepository.save(screen);
    }

    public void softDelete(Long screenId, Long restrictToTheatreId) {
        Screen screen = loadScreen(screenId, restrictToTheatreId);

        if (showRepository.existsByScreenIdAndIsDeletedFalseAndStartTimeAfter(screenId, LocalDateTime.now())) {
            throw new BusinessException(
                    "This screen has upcoming shows. Cancel them before deleting the screen.");
        }
        screen.setIsDeleted(true);
        screenRepository.save(screen);
    }

    /**
     * Persists a custom seat layout. The matrix uses 1 = seat, 0 = pathway, and
     * totalSeats is recomputed from the active cells so the rest of the system
     * stays consistent with what the designer drew.
     */
    public Screen saveLayout(Long screenId, Long restrictToTheatreId,
                             List<List<Integer>> matrix, Integer rows, Integer cols) {
        Screen screen = loadScreen(screenId, restrictToTheatreId);

        if (matrix == null || matrix.isEmpty()) {
            throw new BusinessException("Seat layout matrix cannot be empty.");
        }

        int activeSeats = 0;
        ArrayNode matrixNode = objectMapper.createArrayNode();
        for (List<Integer> row : matrix) {
            ArrayNode rowNode = objectMapper.createArrayNode();
            if (row != null) {
                for (Integer cell : row) {
                    int v = (cell != null && cell == 1) ? 1 : 0;
                    if (v == 1) {
                        activeSeats++;
                    }
                    rowNode.add(v);
                }
            }
            matrixNode.add(rowNode);
        }

        if (activeSeats == 0) {
            throw new BusinessException("A screen must have at least one active seat.");
        }

        int resolvedRows = (rows != null) ? rows : matrix.size();
        int resolvedCols = (cols != null) ? cols
                : matrix.stream().filter(r -> r != null).mapToInt(List::size).max().orElse(0);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("rows", resolvedRows);
        root.put("cols", resolvedCols);
        root.set("matrix", matrixNode);

        try {
            // Built by Jackson rather than string concatenation so the stored JSON
            // cannot be corrupted by a future field or an odd value.
            screen.setLayoutJson(objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            throw new BusinessException("Could not serialize seat layout: " + e.getMessage());
        }

        screen.setTotalSeats(activeSeats);
        return screenRepository.save(screen);
    }

    public Screen getLayout(Long screenId, Long restrictToTheatreId) {
        return loadScreen(screenId, restrictToTheatreId);
    }
}
