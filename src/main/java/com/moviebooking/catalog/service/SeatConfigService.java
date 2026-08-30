package com.moviebooking.catalog.service;

import com.moviebooking.booking.repository.ShowSeatRepository;
import com.moviebooking.catalog.model.*;
import com.moviebooking.catalog.repository.*;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns a screen's physical seating configuration: its pricing tiers and its
 * seat grid. Shared by AdminService and OwnerService.
 *
 * As with ScreenManagementService, the restrictToTheatreId parameter is the only
 * difference between the two callers:
 *   null     -> admin, unrestricted
 *   non-null -> theatre owner, may only touch screens in that theatre
 */
@Service
@Transactional
public class SeatConfigService {

    private final ScreenRepository screenRepository;
    private final SeatTierRepository seatTierRepository;
    private final ScreenSeatRepository screenSeatRepository;
    private final ShowSeatRepository showSeatRepository;

    public SeatConfigService(ScreenRepository screenRepository,
                            SeatTierRepository seatTierRepository,
                            ScreenSeatRepository screenSeatRepository,
                            ShowSeatRepository showSeatRepository) {
        this.screenRepository = screenRepository;
        this.seatTierRepository = seatTierRepository;
        this.screenSeatRepository = screenSeatRepository;
        this.showSeatRepository = showSeatRepository;
    }

    // ------------------------------------------------------------------
    // Access control
    // ------------------------------------------------------------------

    private Screen loadScreen(Long screenId, Long restrictToTheatreId) {
        Screen screen = screenRepository.findByIdAndIsDeletedFalse(screenId)
                .orElseThrow(() -> new ResourceNotFoundException("Screen not found with ID: " + screenId));

        if (restrictToTheatreId != null) {
            Long owning = (screen.getTheatre() != null) ? screen.getTheatre().getId() : null;
            if (!restrictToTheatreId.equals(owning)) {
                // Reported as "not found" rather than "forbidden" so an owner cannot
                // probe which screen IDs exist in other theatres.
                throw new ResourceNotFoundException("Screen not found with ID: " + screenId);
            }
        }
        return screen;
    }

    private SeatTier loadTier(Long tierId, Long restrictToTheatreId) {
        SeatTier tier = seatTierRepository.findByIdAndIsDeletedFalse(tierId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat tier not found with ID: " + tierId));
        loadScreen(tier.getScreen().getId(), restrictToTheatreId);
        return tier;
    }

    // ------------------------------------------------------------------
    // Seat tiers
    // ------------------------------------------------------------------

    public List<SeatTier> listTiers(Long screenId, Long restrictToTheatreId) {
        loadScreen(screenId, restrictToTheatreId);
        return seatTierRepository.findByScreenIdAndIsDeletedFalseOrderByDisplayOrderAscIdAsc(screenId);
    }

    public SeatTier createTier(Long screenId, Long restrictToTheatreId,
                               String name, Integer displayOrder, String colorHex) {
        Screen screen = loadScreen(screenId, restrictToTheatreId);

        if (name == null || name.isBlank()) {
            throw new BusinessException("Tier name is required.");
        }
        String clean = name.trim();
        if (seatTierRepository.existsByScreenIdAndNameIgnoreCaseAndIsDeletedFalse(screenId, clean)) {
            throw new BusinessException("This screen already has a tier named \"" + clean + "\".");
        }

        SeatTier tier = new SeatTier();
        tier.setScreen(screen);
        tier.setName(clean);
        tier.setDisplayOrder(displayOrder != null ? displayOrder : 0);
        tier.setColorHex(normalizeColor(colorHex));
        return seatTierRepository.save(tier);
    }

    public SeatTier updateTier(Long tierId, Long restrictToTheatreId,
                               String name, Integer displayOrder, String colorHex) {
        SeatTier tier = loadTier(tierId, restrictToTheatreId);

        if (name != null && !name.isBlank()) {
            String clean = name.trim();
            if (!clean.equalsIgnoreCase(tier.getName())
                    && seatTierRepository.existsByScreenIdAndNameIgnoreCaseAndIsDeletedFalse(
                            tier.getScreen().getId(), clean)) {
                throw new BusinessException("This screen already has a tier named \"" + clean + "\".");
            }
            tier.setName(clean);
        }
        if (displayOrder != null) {
            tier.setDisplayOrder(displayOrder);
        }
        if (colorHex != null && !colorHex.isBlank()) {
            tier.setColorHex(normalizeColor(colorHex));
        }
        return seatTierRepository.save(tier);
    }

    public void deleteTier(Long tierId, Long restrictToTheatreId) {
        SeatTier tier = loadTier(tierId, restrictToTheatreId);

        // Deleting a tier that seats still reference would leave those seats
        // unpriceable, so the seats have to be reassigned first.
        long assigned = screenSeatRepository.countByTierId(tierId);
        if (assigned > 0) {
            throw new BusinessException(
                    assigned + " seat(s) are still assigned to \"" + tier.getName() + "\". "
                  + "Reassign them in the layout designer before deleting this tier.");
        }
        tier.setIsDeleted(true);
        seatTierRepository.save(tier);
    }

    private String normalizeColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return "#7A7A7A";
        }
        String h = hex.trim();
        if (!h.startsWith("#")) {
            h = "#" + h;
        }
        if (!h.matches("^#[0-9a-fA-F]{6}$")) {
            throw new BusinessException("Colour must be a 6-digit hex value such as #C9A227.");
        }
        return h.toUpperCase();
    }

    // ------------------------------------------------------------------
    // Seat grid
    // ------------------------------------------------------------------

    public List<ScreenSeat> listSeats(Long screenId, Long restrictToTheatreId) {
        loadScreen(screenId, restrictToTheatreId);
        return screenSeatRepository.findByScreenIdAndIsDeletedFalseOrderByRowLabelAscColIndexAsc(screenId);
    }

    /**
     * The rule agreed for Phase 3: a layout may not be rewritten once any show
     * on this screen has materialized its seat map, because those shows would
     * silently keep selling the old arrangement at the old prices.
     *
     * Only materialized shows block. A show that is scheduled but has never been
     * opened has no show_seats rows yet and will generate its seats from the new
     * layout, so it is left alone.
     */
    private void assertLayoutEditable(Long screenId) {
        long materialized = showSeatRepository.countMaterializedForScreen(screenId);
        if (materialized > 0) {
            List<String> shows = showSeatRepository.findMaterializedShowLabelsForScreen(screenId);
            String detail = shows.isEmpty() ? "" : " Affected: " + String.join(", ", shows) + ".";
            throw new BusinessException(
                    "This screen's layout is locked because shows on it have already opened their "
                  + "seat maps." + detail
                  + " Cancel those shows before changing the seating.");
        }
    }

    /**
     * Replaces a screen's entire seat grid.
     *
     * Row labels are derived from the row index (A, B, C...). Seat numbers count
     * only sellable cells within a row, so a pathway in the middle of row C
     * still yields C1..Cn without a gap - which is how cinema seating actually
     * reads. Screen.totalSeats is recomputed from the sellable cells so the rest
     * of the system stays consistent with what was drawn.
     */
    public Screen replaceLayout(Long screenId, Long restrictToTheatreId,
                                List<List<SeatCell>> grid) {
        Screen screen = loadScreen(screenId, restrictToTheatreId);
        assertLayoutEditable(screenId);

        if (grid == null || grid.isEmpty()) {
            throw new BusinessException("Seat layout cannot be empty.");
        }

        List<SeatTier> tiers = seatTierRepository
                .findByScreenIdAndIsDeletedFalseOrderByDisplayOrderAscIdAsc(screenId);
        if (tiers.isEmpty()) {
            throw new BusinessException(
                    "Define at least one seat tier for this screen before drawing its layout.");
        }

        // Remove the previous grid outright. Soft deleting would accumulate dead
        // rows and collide with the position unique constraint on the next save.
        screenSeatRepository.hardDeleteByScreenId(screenId);
        screenSeatRepository.flush();

        List<ScreenSeat> toSave = new ArrayList<>();
        int sellable = 0;

        for (int r = 0; r < grid.size(); r++) {
            List<SeatCell> row = grid.get(r);
            if (row == null) {
                continue;
            }
            String rowLabel = rowLabelFor(r);
            int seatNumber = 0;

            for (int c = 0; c < row.size(); c++) {
                SeatCell cell = row.get(c);
                SeatType type = (cell == null || cell.type() == null) ? SeatType.SEAT : cell.type();

                ScreenSeat seat = new ScreenSeat();
                seat.setScreen(screen);
                seat.setRowLabel(rowLabel);
                seat.setColIndex(c);
                seat.setSeatType(type);

                if (type == SeatType.SEAT) {
                    seatNumber++;
                    sellable++;
                    seat.setSeatNumber(seatNumber);

                    Long tierId = (cell == null) ? null : cell.tierId();
                    SeatTier tier = resolveTier(tierId, tiers, rowLabel, seatNumber);
                    seat.setSeatTier(tier);
                } else {
                    seat.setSeatNumber(null);
                    seat.setSeatTier(null);
                }
                toSave.add(seat);
            }
        }

        if (sellable == 0) {
            throw new BusinessException("A screen must have at least one sellable seat.");
        }

        screenSeatRepository.saveAll(toSave);
        screen.setTotalSeats(sellable);
        return screenRepository.save(screen);
    }

    private SeatTier resolveTier(Long tierId, List<SeatTier> tiers, String rowLabel, int seatNumber) {
        if (tierId == null) {
            // Unassigned sellable cells fall back to the lowest-ordered tier
            // rather than failing, so a designer save is never rejected purely
            // because one cell was left unpainted.
            return tiers.get(0);
        }
        return tiers.stream()
                .filter(t -> t.getId().equals(tierId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Seat " + rowLabel + seatNumber + " references tier " + tierId
                      + ", which does not belong to this screen."));
    }

    /** 0 -> "A", 25 -> "Z", 26 -> "AA". */
    private String rowLabelFor(int index) {
        StringBuilder sb = new StringBuilder();
        int i = index;
        while (true) {
            sb.insert(0, (char) ('A' + (i % 26)));
            i = i / 26 - 1;
            if (i < 0) {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * Changes the tier of a single seat. This is the per-seat CRUD operation -
     * useful for upgrading one row without redrawing the whole grid, and it is
     * subject to the same lock as a full layout rewrite.
     */
    public ScreenSeat updateSeatTier(Long seatId, Long restrictToTheatreId, Long tierId) {
        ScreenSeat seat = screenSeatRepository.findByIdAndIsDeletedFalse(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat not found with ID: " + seatId));

        Long screenId = seat.getScreen().getId();
        loadScreen(screenId, restrictToTheatreId);
        assertLayoutEditable(screenId);

        if (seat.getSeatType() != SeatType.SEAT) {
            throw new BusinessException("Only sellable seats can be assigned a tier.");
        }
        SeatTier tier = loadTier(tierId, restrictToTheatreId);
        if (!tier.getScreen().getId().equals(screenId)) {
            throw new BusinessException("That tier belongs to a different screen.");
        }
        seat.setSeatTier(tier);
        return screenSeatRepository.save(seat);
    }

    /** A single cell submitted by the layout designer. */
    public record SeatCell(SeatType type, Long tierId) {}
}
