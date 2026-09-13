package com.moviebooking.ops.service;

import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.catalog.model.Screen;
import com.moviebooking.catalog.model.Show;
import com.moviebooking.catalog.model.Theatre;
import com.moviebooking.catalog.repository.ScreenRepository;
import com.moviebooking.catalog.repository.ShowRepository;
import com.moviebooking.catalog.repository.TheatreRepository;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.exception.ResourceNotFoundException;
import com.moviebooking.ops.dto.OpsDTOs.CreateIncidentRequest;
import com.moviebooking.ops.dto.OpsDTOs.UpdateIncidentRequest;
import com.moviebooking.ops.dto.OpsDTOs.IncidentResponse;
import com.moviebooking.ops.model.*;
import com.moviebooking.ops.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidentRepository;
    private final TheatreRepository theatreRepository;
    private final ScreenRepository screenRepository;
    private final ShowRepository showRepository;
    private final UserRepository userRepository;

    public IncidentService(IncidentRepository incidentRepository,
                           TheatreRepository theatreRepository,
                           ScreenRepository screenRepository,
                           ShowRepository showRepository,
                           UserRepository userRepository) {
        this.incidentRepository = incidentRepository;
        this.theatreRepository = theatreRepository;
        this.screenRepository = screenRepository;
        this.showRepository = showRepository;
        this.userRepository = userRepository;
    }

    public IncidentResponse createIncident(CreateIncidentRequest req, Long restrictToTheatreId) {
        // Validate theatre
        Long theatreId = req.theatreId();
        Theatre theatre = theatreRepository.findByIdAndIsDeletedFalse(theatreId)
                .orElseThrow(() -> new ResourceNotFoundException("Theatre not found with ID: " + theatreId));

        // Scope check
        if (restrictToTheatreId != null && !theatreId.equals(restrictToTheatreId)) {
            throw new BusinessException("Access denied: cannot create incident for another theatre.");
        }

        // Validate optional screen belongs to the theatre
        if (req.screenId() != null) {
            Screen screen = screenRepository.findByIdAndIsDeletedFalse(req.screenId())
                    .orElseThrow(() -> new ResourceNotFoundException("Screen not found with ID: " + req.screenId()));
            if (!screen.getTheatre().getId().equals(theatreId)) {
                throw new BusinessException("Access denied: screen does not belong to the specified theatre.");
            }
        }

        // Validate optional show belongs to the theatre (Show → Screen → Theatre chain)
        if (req.showId() != null) {
            Show show = showRepository.findByIdAndIsDeletedFalse(req.showId())
                    .orElseThrow(() -> new ResourceNotFoundException("Show not found with ID: " + req.showId()));
            if (!show.getScreen().getTheatre().getId().equals(theatreId)) {
                throw new BusinessException("Access denied: show does not belong to the specified theatre.");
            }
        }

        // Get current user
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        Incident incident = new Incident();
        incident.setType(req.type());
        incident.setSeverity(req.severity());
        incident.setStatus(IncidentStatus.OPEN);
        incident.setTheatre(theatre);
        incident.setScreen(req.screenId() != null ? screenRepository.findById(req.screenId()).orElse(null) : null);
        incident.setShow(req.showId() != null ? showRepository.findById(req.showId()).orElse(null) : null);
        incident.setDescription(req.description());
        incident.setIncidentStartTime(req.incidentStartTime());
        incident.setReportedTime(req.reportedTime() != null ? req.reportedTime() : LocalDateTime.now());
        incident.setCreatedBy(currentUser);

        Incident saved = incidentRepository.save(incident);
        log.info("Incident {} created for theatre {} by user {}", saved.getId(), theatreId, email);

        return toResponse(saved);
    }

    public IncidentResponse updateIncident(Long incidentId, UpdateIncidentRequest req, Long restrictToTheatreId) {
        Incident incident = getIncidentWithScopeCheck(incidentId, restrictToTheatreId);

        if (req.type() != null) incident.setType(req.type());
        if (req.severity() != null) incident.setSeverity(req.severity());
        if (req.description() != null) incident.setDescription(req.description());

        // Status changes should go through the dedicated close endpoint
        if (req.status() != null && req.status() != IncidentStatus.CLOSED) {
            incident.setStatus(req.status());
        }

        Incident saved = incidentRepository.save(incident);
        return toResponse(saved);
    }

    public IncidentResponse closeIncident(Long incidentId, Long restrictToTheatreId) {
        Incident incident = getIncidentWithScopeCheck(incidentId, restrictToTheatreId);

        if (incident.getStatus() == IncidentStatus.CLOSED) {
            throw new BusinessException("Incident is already closed.");
        }

        incident.setStatus(IncidentStatus.CLOSED);
        incident.setClosedAt(LocalDateTime.now());

        Incident saved = incidentRepository.save(incident);
        log.info("Incident {} closed", incidentId);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidents(Long theatreId, Long restrictToTheatreId) {
        if (restrictToTheatreId != null) {
            theatreId = restrictToTheatreId;
        }

        List<Incident> incidents;
        if (theatreId != null) {
            incidents = incidentRepository.findByTheatreId(theatreId);
        } else {
            incidents = incidentRepository.findAllActive();
        }

        return incidents.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(Long incidentId, Long restrictToTheatreId) {
        Incident incident = getIncidentWithScopeCheck(incidentId, restrictToTheatreId);
        return toResponse(incident);
    }

    // ================= HELPER =================

    private Incident getIncidentWithScopeCheck(Long incidentId, Long restrictToTheatreId) {
        Incident incident = incidentRepository.findByIdAndIsDeletedFalse(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with ID: " + incidentId));

        if (restrictToTheatreId != null && !incident.getTheatre().getId().equals(restrictToTheatreId)) {
            throw new BusinessException("Access denied: incident does not belong to your theatre.");
        }

        return incident;
    }

    private IncidentResponse toResponse(Incident i) {
        return new IncidentResponse(
                i.getId(),
                i.getType().name(),
                i.getSeverity().name(),
                i.getStatus().name(),
                i.getTheatre().getId(),
                i.getTheatre().getName(),
                i.getScreen() != null ? i.getScreen().getId() : null,
                i.getScreen() != null ? i.getScreen().getName() : null,
                i.getShow() != null ? i.getShow().getId() : null,
                i.getShow() != null ? i.getShow().getMovie().getTitle() : null,
                i.getDescription(),
                i.getIncidentStartTime(),
                i.getReportedTime(),
                i.getClosedAt(),
                i.getCreatedBy().getId(),
                i.getCreatedBy().getName(),
                i.getCreatedAt()
        );
    }
}
