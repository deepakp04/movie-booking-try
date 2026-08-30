package com.moviebooking.stream.service;

import com.moviebooking.stream.dto.SeatUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages SSE sinks for real-time seat updates.
 * Each show has its own broadcast sink that emits SeatUpdateEvents to all connected clients.
 */
@Service
public class SeatStreamService {

    private static final Logger log = LoggerFactory.getLogger(SeatStreamService.class);

    // One sink per showId - broadcasts to all connected clients watching that show
    private final Map<Long, Sinks.Many<SeatUpdateEvent>> showSinks = new ConcurrentHashMap<>();

    /**
     * Get or create a Flux for a specific show's seat updates.
     * Clients subscribe to this Flux to receive real-time seat status changes.
     */
    public Flux<SeatUpdateEvent> getSeatUpdatesForShow(Long showId) {
        Sinks.Many<SeatUpdateEvent> sink = showSinks.computeIfAbsent(
            showId, 
            id -> Sinks.many().multicast().onBackpressureBuffer()
        );
        
        return sink.asFlux()
            .doOnCancel(() -> {
                log.debug("Client disconnected from show {} seat updates", showId);
                cleanupIfEmpty(showId);
            })
            .doOnError(e -> log.error("Error in seat update stream for show {}", showId, e));
    }

    /**
     * Broadcast a seat update event to all clients watching this show.
     */
    public void broadcastSeatUpdate(Long showId, SeatUpdateEvent event) {
        Sinks.Many<SeatUpdateEvent> sink = showSinks.get(showId);
        if (sink != null) {
            sink.tryEmitNext(event);
            log.debug("Broadcast seat update for show {}: {} -> {}", showId, event.seatCode(), event.status());
        }
    }

    /**
     * Remove a show's sink when no longer needed (optional cleanup).
     */
    public void removeShowSink(Long showId) {
        Sinks.Many<SeatUpdateEvent> sink = showSinks.remove(showId);
        if (sink != null) {
            sink.tryEmitComplete();
            log.info("Removed seat update sink for show {}", showId);
        }
    }

    /**
     * Clean up empty sinks (no subscribers) to prevent memory leaks.
     */
    private void cleanupIfEmpty(Long showId) {
        Sinks.Many<SeatUpdateEvent> sink = showSinks.get(showId);
        if (sink != null && sink.currentSubscriberCount() == 0) {
            // Delay cleanup slightly to allow reconnection
            try {
                Thread.sleep(5000);
                if (sink.currentSubscriberCount() == 0) {
                    showSinks.remove(showId, sink);
                    sink.tryEmitComplete();
                    log.debug("Cleaned up empty sink for show {}", showId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
