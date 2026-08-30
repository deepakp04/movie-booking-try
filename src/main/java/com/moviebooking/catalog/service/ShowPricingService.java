package com.moviebooking.catalog.service;

import com.moviebooking.catalog.model.SeatTier;
import com.moviebooking.catalog.model.Show;
import com.moviebooking.catalog.model.ShowTierPrice;
import com.moviebooking.catalog.repository.SeatTierRepository;
import com.moviebooking.catalog.repository.ShowTierPriceRepository;
import com.moviebooking.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-show, per-tier pricing.
 *
 * Price lives on the show rather than the tier because the same DIAMOND row
 * costs different amounts for a Tuesday matinee and a Saturday IMAX screening.
 *
 * Resolution order for any seat is:
 *   1. show_tier_prices for (show, tier)
 *   2. Show.basePrice
 *
 * Step 2 is what keeps shows created before this table existed sellable, so no
 * backfill is required for historical data.
 */
@Service
@Transactional
public class ShowPricingService {

    private final ShowTierPriceRepository showTierPriceRepository;
    private final SeatTierRepository seatTierRepository;

    public ShowPricingService(ShowTierPriceRepository showTierPriceRepository,
                              SeatTierRepository seatTierRepository) {
        this.showTierPriceRepository = showTierPriceRepository;
        this.seatTierRepository = seatTierRepository;
    }

    /**
     * Writes the tier prices for a show, replacing any existing ones.
     *
     * Every active tier on the show's screen must be priced. A missing tier would
     * silently fall back to basePrice and make a whole seating class the wrong
     * price, which is exactly the kind of quiet pricing error that is very hard
     * to notice after the fact.
     */
    public void saveTierPrices(Show show, List<TierPriceInput> prices) {
        Long screenId = show.getScreen().getId();
        List<SeatTier> tiers =
                seatTierRepository.findByScreenIdAndIsDeletedFalseOrderByDisplayOrderAscIdAsc(screenId);

        // Screens with no tiers configured yet keep working on basePrice alone.
        if (tiers.isEmpty()) {
            return;
        }

        Map<Long, BigDecimal> submitted = new HashMap<>();
        if (prices != null) {
            for (TierPriceInput p : prices) {
                if (p == null || p.tierId() == null) {
                    continue;
                }
                if (p.price() == null || p.price().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("Each tier price must be greater than zero.");
                }
                submitted.put(p.tierId(), p.price());
            }
        }

        List<String> missing = new ArrayList<>();
        for (SeatTier t : tiers) {
            if (!submitted.containsKey(t.getId())) {
                missing.add(t.getName());
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(
                    "A price is required for every seat tier on this screen. Missing: "
                  + String.join(", ", missing) + ".");
        }

        for (SeatTier t : tiers) {
            ShowTierPrice row = showTierPriceRepository
                    .findByShowIdAndSeatTierIdAndIsDeletedFalse(show.getId(), t.getId())
                    .orElseGet(() -> {
                        ShowTierPrice fresh = new ShowTierPrice();
                        fresh.setShow(show);
                        fresh.setSeatTier(t);
                        return fresh;
                    });
            row.setPrice(submitted.get(t.getId()));
            showTierPriceRepository.save(row);
        }
    }

    public List<ShowTierPrice> listPrices(Long showId) {
        return showTierPriceRepository.findByShowIdAndIsDeletedFalse(showId);
    }

    /**
     * The price of one seat in one show. Used when materializing a show's seat
     * map, where it is snapshotted onto the show_seat row so that a later price
     * edit cannot rewrite what a customer already paid.
     */
    public BigDecimal resolvePrice(Show show, SeatTier tier) {
        if (tier != null) {
            return showTierPriceRepository
                    .findByShowIdAndSeatTierIdAndIsDeletedFalse(show.getId(), tier.getId())
                    .map(ShowTierPrice::getPrice)
                    .orElse(show.getBasePrice());
        }
        return show.getBasePrice();
    }

    /** Package-neutral input so admin and owner DTOs can both feed this. */
    public record TierPriceInput(Long tierId, BigDecimal price) {}
}
