package com.moviebooking.stream.controller;

import com.moviebooking.stream.dto.SeatUpdateEvent;
import com.moviebooking.stream.service.SeatStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Reactive controller for Server-Sent Events (SSE) streaming.
 * Provides real-time seat availability updates to connected clients.
 */
@RestController
@RequestMapping("/api/stream")
public class StreamController {

    private static final Logger log = LoggerFactory.getLogger(StreamController.class);

    private final SeatStreamService seatStreamService;

    public StreamController(SeatStreamService seatStreamService) {
        this.seatStreamService = seatStreamService;
    }

    /**
     * SSE endpoint for real-time seat updates.
     * Clients connect to this endpoint and receive seat status changes as they happen.
     * 
     * Usage in browser:
     * const eventSource = new EventSource('/api/stream/shows/123/seats');
     * eventSource.onmessage = (event) => {
     *     const update = JSON.parse(event.data);
     *     // update.showId, update.seatCode, update.status, update.reason
     * };
     */
    @GetMapping(value = "/shows/{showId}/seats", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<SeatUpdateEvent> streamSeatUpdates(@PathVariable Long showId) {
        log.info("Client connecting to seat update stream for show {}", showId);
        
        return seatStreamService.getSeatUpdatesForShow(showId)
            .doOnSubscribe(subscription -> 
                log.debug("Client subscribed to show {} seat updates", showId))
            .doOnComplete(() -> 
                log.info("Seat update stream completed for show {}", showId))
            .doOnError(e -> 
                log.error("Error in seat update stream for show {}", showId, e));
    }
}
