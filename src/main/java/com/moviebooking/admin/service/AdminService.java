package com.moviebooking.admin.service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moviebooking.admin.dto.AdminDTOs.AssignOwnerRequest;
import com.moviebooking.admin.dto.AdminDTOs.CityRequest;
import com.moviebooking.admin.dto.AdminDTOs.CityResponse;
import com.moviebooking.admin.dto.AdminDTOs.CityUpdateRequest;
import com.moviebooking.admin.dto.AdminDTOs.LayoutSaveRequest;
import com.moviebooking.admin.dto.AdminDTOs.MovieRequest;
import com.moviebooking.admin.dto.AdminDTOs.MovieResponse;
import com.moviebooking.admin.dto.AdminDTOs.MovieUpdateRequest;
import com.moviebooking.admin.dto.AdminDTOs.ScreenLayoutDetailResponse;
import com.moviebooking.admin.dto.AdminDTOs.ScreenLayoutResponse;
import com.moviebooking.admin.dto.AdminDTOs.ScreenRequest;
import com.moviebooking.admin.dto.AdminDTOs.ScreenResponse;
import com.moviebooking.admin.dto.AdminDTOs.ScreenSeatResponse;
import com.moviebooking.admin.dto.AdminDTOs.ScreenUpdateRequest;
import com.moviebooking.admin.dto.AdminDTOs.SeatCellRequest;
import com.moviebooking.admin.dto.AdminDTOs.SeatLayoutRequest;
import com.moviebooking.admin.dto.AdminDTOs.SeatTierAssignRequest;
import com.moviebooking.admin.dto.AdminDTOs.SeatTierRequest;
import com.moviebooking.admin.dto.AdminDTOs.SeatTierResponse;
import com.moviebooking.admin.dto.AdminDTOs.ShowRequest;
import com.moviebooking.admin.dto.AdminDTOs.ShowResponse;
import com.moviebooking.admin.dto.AdminDTOs.TheatreRequest;
import com.moviebooking.admin.dto.AdminDTOs.TheatreResponse;
import com.moviebooking.admin.dto.AdminDTOs.TheatreUpdateRequest;
import com.moviebooking.admin.dto.AdminDTOs.TierPriceRequest;
import com.moviebooking.admin.dto.AdminDTOs.TierPriceResponse;
import com.moviebooking.auth.entity.User;
import com.moviebooking.auth.repository.UserRepository;
import com.moviebooking.booking.dto.BookingDTOs.BookingResponse;
import com.moviebooking.catalog.model.City;
import com.moviebooking.catalog.model.Movie;
import com.moviebooking.catalog.model.MovieFormat;
import com.moviebooking.catalog.model.Screen;
import com.moviebooking.catalog.model.ScreenSeat;
import com.moviebooking.catalog.model.SeatTier;
import com.moviebooking.catalog.model.SeatType;
import com.moviebooking.catalog.model.Show;
import com.moviebooking.catalog.model.Theatre;
import com.moviebooking.catalog.repository.CityRepository;
import com.moviebooking.catalog.repository.MovieRepository;
import com.moviebooking.catalog.repository.ScreenRepository;
import com.moviebooking.catalog.repository.ScreenSeatRepository;
import com.moviebooking.catalog.repository.ShowRepository;
import com.moviebooking.catalog.repository.TheatreRepository;
import com.moviebooking.catalog.service.ScreenManagementService;
import com.moviebooking.catalog.service.SeatConfigService;
import com.moviebooking.catalog.service.ShowPricingService;
import com.moviebooking.common.constants.Role;
import com.moviebooking.common.constants.UserStatus;
import com.moviebooking.common.exception.BusinessException;
import com.moviebooking.common.exception.ResourceNotFoundException;

@Service
@Transactional
public class AdminService {

    private final CityRepository cityRepository;
    private final MovieRepository movieRepository;
    private final TheatreRepository theatreRepository;
    private final ScreenRepository screenRepository;
    private final ShowRepository showRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ScreenManagementService screenManagement;
    private final SeatConfigService seatConfig;
    private final ShowPricingService showPricing;
    private final ScreenSeatRepository screenSeatRepository;
    private final com.moviebooking.booking.repository.ShowSeatRepository showSeatRepository;
    private final com.moviebooking.booking.service.BookingService bookingService;

    public AdminService(CityRepository cityRepository,
                        MovieRepository movieRepository,
                        TheatreRepository theatreRepository,
                        ScreenRepository screenRepository,
                        ShowRepository showRepository,
                        UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        ScreenManagementService screenManagement,
                        SeatConfigService seatConfig,
                        ShowPricingService showPricing,
                        ScreenSeatRepository screenSeatRepository,
                        com.moviebooking.booking.repository.ShowSeatRepository showSeatRepository,
                        com.moviebooking.booking.service.BookingService bookingService) {
        this.cityRepository = cityRepository;
        this.movieRepository = movieRepository;
        this.theatreRepository = theatreRepository;
        this.screenRepository = screenRepository;
        this.showRepository = showRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.screenManagement = screenManagement;
        this.seatConfig = seatConfig;
        this.showPricing = showPricing;
        this.screenSeatRepository = screenSeatRepository;
        this.showSeatRepository = showSeatRepository;
        this.bookingService = bookingService;
    }

    // --- CITIES ---
    public CityResponse createCity(CityRequest req) {
        City city = new City();
        city.setName(req.name());
        city.setState(req.state());
        return mapToCityResponse(cityRepository.save(city));
    }

    public List<CityResponse> getAllCities() {
        return cityRepository.findByIsDeletedFalseOrderByNameAsc().stream()
                .map(this::mapToCityResponse)
                .collect(Collectors.toList());
    }

    public CityResponse updateCity(Long cityId, CityUpdateRequest req) {
        City c = cityRepository.findByIdAndIsDeletedFalse(cityId)
                .orElseThrow(() -> new ResourceNotFoundException("City not found with ID: " + cityId));

        if (req.name() != null && !req.name().isBlank()) {
            c.setName(req.name().trim());
        }
        if (req.state() != null && !req.state().isBlank()) {
            c.setState(req.state().trim());
        }
        return mapToCityResponse(cityRepository.save(c));
    }

    // Soft delete. A hard delete would break the FK from theatres and, through
    // them, the whole booking history that Analytics has to report on.
    public void deleteCity(Long id) {
        City c = cityRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("City not found with ID: " + id));

        if (theatreRepository.existsByCityIdAndIsDeletedFalse(id)) {
            throw new BusinessException(
                    "This city still has active theatres. Remove or reassign them first.");
        }
        c.setIsDeleted(true);
        cityRepository.save(c);
    }

    private CityResponse mapToCityResponse(City c) {
        return new CityResponse(c.getId(), c.getName(), c.getState());
    }

    // --- MOVIES ---
    public MovieResponse createMovie(MovieRequest req) {
        Movie movie = new Movie();
        movie.setTitle(req.title());
        movie.setDescription(req.description());
        movie.setCastMembers(req.castMembers());
        movie.setDirector(req.director());
        movie.setDurationMinutes(req.durationMinutes());
        movie.setCbfcRating(req.cbfcRating());
        movie.setPosterUrl(req.posterUrl());
        movie.setBannerUrl(req.bannerUrl());
        movie.setReleaseDate(req.releaseDate());

        // Previously dropped silently: the entity had no such fields, so the
        // admin UI sent languages/formats that never reached the database and
        // the movie table then crashed on undefined.join().
        if (req.availableLanguages() != null) {
            movie.setAvailableLanguages(new LinkedHashSet<>(req.availableLanguages()));
        }
        if (req.availableFormats() != null) {
            movie.setAvailableFormats(new LinkedHashSet<>(req.availableFormats()));
        }
        return mapToMovieResponse(movieRepository.save(movie));
    }

    public List<MovieResponse> getAllMovies() {
        return movieRepository.findByIsDeletedFalseOrderByTitleAsc().stream()
                .map(this::mapToMovieResponse)
                .collect(Collectors.toList());
    }

    public MovieResponse updateMovie(Long movieId, MovieUpdateRequest req) {
        Movie m = movieRepository.findByIdAndIsDeletedFalse(movieId)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with ID: " + movieId));

        if (req.title() != null && !req.title().isBlank()) m.setTitle(req.title().trim());
        if (req.description() != null) m.setDescription(req.description());
        if (req.castMembers() != null) m.setCastMembers(req.castMembers());
        if (req.director() != null) m.setDirector(req.director());
        if (req.cbfcRating() != null) m.setCbfcRating(req.cbfcRating());
        if (req.posterUrl() != null) m.setPosterUrl(req.posterUrl());
        if (req.bannerUrl() != null) m.setBannerUrl(req.bannerUrl());
        if (req.releaseDate() != null) m.setReleaseDate(req.releaseDate());

        if (req.durationMinutes() != null) {
            if (req.durationMinutes() < 1) {
                throw new BusinessException("Duration must be at least 1 minute.");
            }
            m.setDurationMinutes(req.durationMinutes());
        }

        // Replaced wholesale rather than merged: the UI submits the complete set,
        // so merging would make removing a language impossible.
        if (req.availableLanguages() != null) {
            m.getAvailableLanguages().clear();
            m.getAvailableLanguages().addAll(req.availableLanguages());
        }
        if (req.availableFormats() != null) {
            m.getAvailableFormats().clear();
            m.getAvailableFormats().addAll(req.availableFormats());
        }
        return mapToMovieResponse(movieRepository.save(m));
    }

    public void deleteMovie(Long id) {
        Movie m = movieRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with ID: " + id));

        if (showRepository.existsByMovieIdAndIsDeletedFalseAndStartTimeAfter(id, LocalDateTime.now())) {
            throw new BusinessException(
                    "This movie has upcoming shows scheduled. Cancel them before removing it "
                  + "from the library.");
        }
        m.setIsDeleted(true);
        movieRepository.save(m);
    }

    // Built inside the transaction so the LAZY @ElementCollections initialize
    // before Jackson serializes - required now that open-in-view is disabled.
    private MovieResponse mapToMovieResponse(Movie m) {
        List<String> languages = (m.getAvailableLanguages() == null) ? List.of()
                : m.getAvailableLanguages().stream().map(Enum::name).collect(Collectors.toList());

        List<String> formats = (m.getAvailableFormats() == null) ? List.of()
                : m.getAvailableFormats().stream().map(MovieFormat::getValue).collect(Collectors.toList());

        return new MovieResponse(
                m.getId(), m.getTitle(), m.getDescription(), m.getCastMembers(),
                m.getDirector(), m.getDurationMinutes(),
                m.getCbfcRating() != null ? m.getCbfcRating().name() : null,
                m.getPosterUrl(), m.getBannerUrl(), m.getReleaseDate(),
                languages, formats
        );
    }

    // --- THEATRES & SCREENS ---
    public TheatreResponse createTheatre(TheatreRequest req) {
        City city = cityRepository.findByIdAndIsDeletedFalse(req.cityId())
                .orElseThrow(() -> new ResourceNotFoundException("City not found with ID: " + req.cityId()));

        Theatre theatre = new Theatre();
        theatre.setName(req.name());
        theatre.setAddress(req.address());
        theatre.setCity(city);
        Theatre saved = theatreRepository.save(theatre);
        return mapToTheatreResponse(saved);
    }

    public List<TheatreResponse> getAllTheatres() {
        return theatreRepository.findByIsDeletedFalseOrderByNameAsc().stream()
                .map(this::mapToTheatreResponse)
                .collect(Collectors.toList());
    }

    public List<TheatreResponse> getTheatresByCity(Long cityId) {
        return theatreRepository.findByCityIdAndIsDeletedFalse(cityId).stream()
                .map(this::mapToTheatreResponse)
                .collect(Collectors.toList());
    }

    public TheatreResponse updateTheatre(Long theatreId, TheatreUpdateRequest req) {
        Theatre theatre = theatreRepository.findByIdAndIsDeletedFalse(theatreId)
                .orElseThrow(() -> new ResourceNotFoundException("Theatre not found with ID: " + theatreId));

        if (req.name() != null && !req.name().isBlank()) theatre.setName(req.name().trim());
        if (req.address() != null && !req.address().isBlank()) theatre.setAddress(req.address().trim());

        if (req.cityId() != null
                && (theatre.getCity() == null || !req.cityId().equals(theatre.getCity().getId()))) {
            City city = cityRepository.findByIdAndIsDeletedFalse(req.cityId())
                    .orElseThrow(() -> new ResourceNotFoundException("City not found with ID: " + req.cityId()));
            theatre.setCity(city);
        }
        return mapToTheatreResponse(theatreRepository.save(theatre));
    }

    // Soft delete, and it refuses while future shows exist so the admin has to
    // consciously cancel them first. That is what protects booking history.
    public void deleteTheatre(Long theatreId) {
        Theatre theatre = theatreRepository.findByIdAndIsDeletedFalse(theatreId)
                .orElseThrow(() -> new ResourceNotFoundException("Theatre not found with ID: " + theatreId));

        if (showRepository.existsByScreenTheatreIdAndIsDeletedFalseAndStartTimeAfter(
                theatreId, LocalDateTime.now())) {
            throw new BusinessException(
                    "This theatre has upcoming shows. Cancel them before deleting the theatre.");
        }

        // Cascade the soft delete so the screens stop appearing everywhere too.
        for (Screen s : screenRepository.findByTheatreIdAndIsDeletedFalse(theatreId)) {
            s.setIsDeleted(true);
            screenRepository.save(s);
        }
        theatre.setIsDeleted(true);
        theatreRepository.save(theatre);
    }

    public ScreenResponse createScreen(Long theatreId, ScreenRequest req) {
        Theatre theatre = theatreRepository.findByIdAndIsDeletedFalse(theatreId)
                .orElseThrow(() -> new ResourceNotFoundException("Theatre not found with ID: " + theatreId));

        Screen screen = new Screen();
        screen.setName(req.name());
        // Capacity is derived from the seat layout drawn in the Maintenance tab,
        // so a screen starts at zero seats rather than asking for a number that
        // the layout would immediately contradict.
        screen.setTotalSeats(req.totalSeats() != null ? req.totalSeats() : 0);
        screen.setTheatre(theatre);
        Screen saved = screenRepository.save(screen);

        // Keep both sides of the association in sync. Setting only the owning
        // side left theatre.screens stale for the rest of the transaction, which
        // is why a new screen appeared to vanish until a full reload.
        if (theatre.getScreens() != null) {
            theatre.getScreens().add(saved);
        }
        return toScreenResponse(saved);
    }

    public List<ScreenResponse> getScreensByTheatre(Long theatreId) {
        return screenRepository.findByTheatreIdAndIsDeletedFalse(theatreId).stream()
                .map(this::toScreenResponse)
                .collect(Collectors.toList());
    }

    public ScreenResponse updateScreen(Long screenId, ScreenUpdateRequest req) {
        return toScreenResponse(screenManagement.rename(screenId, null, req.name(), req.totalSeats()));
    }

    public void deleteScreen(Long screenId) {
        screenManagement.softDelete(screenId, null);
    }

    public ScreenLayoutResponse updateScreenLayout(Long screenId, SeatLayoutRequest req) {
        Screen s = screenManagement.saveLayout(screenId, null, req.matrix(), req.rows(), req.cols());
        return new ScreenLayoutResponse(s.getId(), s.getName(), s.getTotalSeats(), s.getLayoutJson());
    }

    // Needed so the seat designer can load an existing layout instead of
    // resetting to a blank grid and overwriting the saved one on save.
    public ScreenLayoutResponse getScreenLayout(Long screenId) {
        Screen s = screenManagement.getLayout(screenId, null);
        return new ScreenLayoutResponse(s.getId(), s.getName(), s.getTotalSeats(), s.getLayoutJson());
    }

    private ScreenResponse toScreenResponse(Screen s) {
        return new ScreenResponse(s.getId(), s.getName(), s.getTotalSeats(), s.getLayoutJson());
    }

    // --- THEATRE OWNER ASSIGNMENT ---
    // Admin-provisioned: creates a THEATRE_OWNER user (pre-verified & active,
    // no OTP flow needed since admin vouches for identity) and links it to
    // exactly one theatre. Enforces one-owner-per-theatre.
    public TheatreResponse assignOwner(Long theatreId, AssignOwnerRequest req) {
        Theatre theatre = theatreRepository.findByIdAndIsDeletedFalse(theatreId)
                .orElseThrow(() -> new ResourceNotFoundException("Theatre not found with ID: " + theatreId));

        if (theatre.getOwner() != null) {
            throw new BusinessException("This theatre already has an owner assigned. Unassign the current owner first.");
        }
        if (userRepository.existsByEmailAndIsDeletedFalse(req.email())) {
            throw new BusinessException("A user with this email already exists.");
        }

        User owner = new User();
        owner.setName(req.name());
        owner.setEmail(req.email());
        owner.setPasswordHash(passwordEncoder.encode(req.password()));
        owner.setRole(Role.THEATRE_OWNER);
        owner.setIsEmailVerified(true);
        owner.setStatus(UserStatus.ACTIVE);
        User savedOwner = userRepository.save(owner);

        theatre.setOwner(savedOwner);
        Theatre saved = theatreRepository.save(theatre);
        return mapToTheatreResponse(saved);
    }

    public TheatreResponse unassignOwner(Long theatreId) {
        Theatre theatre = theatreRepository.findByIdAndIsDeletedFalse(theatreId)
                .orElseThrow(() -> new ResourceNotFoundException("Theatre not found with ID: " + theatreId));

        theatre.setOwner(null);
        Theatre saved = theatreRepository.save(theatre);
        return mapToTheatreResponse(saved);
    }

    // Helper mapping method to keep responses clean and cycle-free
    private TheatreResponse mapToTheatreResponse(Theatre t) {
        List<ScreenResponse> screens = (t.getScreens() == null) ? List.of() :
                t.getScreens().stream()
                        .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                        .map(this::toScreenResponse)
                        .collect(Collectors.toList());

        String cityName = t.getCity() != null ? t.getCity().getName() : "";
        Long cityId = t.getCity() != null ? t.getCity().getId() : null;

        Long ownerId = t.getOwner() != null ? t.getOwner().getId() : null;
        String ownerName = t.getOwner() != null ? t.getOwner().getName() : null;
        String ownerEmail = t.getOwner() != null ? t.getOwner().getEmail() : null;

        return new TheatreResponse(t.getId(), t.getName(), t.getAddress(), cityId, cityName, screens,
                ownerId, ownerName, ownerEmail);
    }

    // --- SHOWS ---
    public ShowResponse scheduleShow(ShowRequest req) {
        Screen screen = screenRepository.findByIdAndIsDeletedFalse(req.screenId())
                .orElseThrow(() -> new ResourceNotFoundException("Screen not found with ID: " + req.screenId()));

        Movie movie = movieRepository.findByIdAndIsDeletedFalse(req.movieId())
                .orElseThrow(() -> new ResourceNotFoundException("Movie not found with ID: " + req.movieId()));

        Show show = new Show();
        show.setScreen(screen);
        show.setMovie(movie);
        show.setStartTime(req.startTime());
        show.setLanguage(req.language());
        show.setFormat(req.format());
        show.setHasCaptions(req.hasCaptions() != null ? req.hasCaptions() : false);
        show.setBasePrice(req.basePrice());

        // Show.basePrice is NOT NULL and acts as the fallback for any seat whose
        // tier has no explicit price, so derive it from the cheapest tier rather
        // than making the user enter a number that duplicates the tier prices.
        if (show.getBasePrice() == null) {
            show.setBasePrice(cheapestTierPrice(req.tierPrices()));
        }
        Show saved = showRepository.save(show);

        // Pricing is per show: one price per seat tier on the chosen screen.
        showPricing.saveTierPrices(saved, toPricingInputs(req.tierPrices()));

        // Reserve seats at scheduling time if requested (e.g. house seats,
        // complimentary blocks). These seats are marked BOOKED immediately.
        if (req.reservedSeatCodes() != null && !req.reservedSeatCodes().isEmpty()) {
            reserveSeatsAtScheduling(saved, req.reservedSeatCodes());
        }

        return mapToShowResponse(saved);
    }

    /**
     * Reserves specific seats when a show is scheduled. Used for house seats,
     * complimentary blocks, or technical holds (e.g. projector view obstruction).
     * The seats are marked BOOKED with a zero-value booking so they never appear
     * available to customers.
     */
    private void reserveSeatsAtScheduling(Show show, List<String> seatCodes) {
        // Ensure the show's seat map is materialized first
        show = bookingService.ensureSeatsInitialized(show.getId());

        List<com.moviebooking.booking.model.ShowSeat> showSeats =
                showSeatRepository.findByShowId(show.getId());

        Map<String, com.moviebooking.booking.model.ShowSeat> byCode =
                showSeats.stream().collect(Collectors.toMap(s -> s.getSeatCode(), s -> s));

        List<String> invalid = seatCodes.stream()
                .filter(code -> !byCode.containsKey(code))
                .toList();
        if (!invalid.isEmpty()) {
            throw new BusinessException(
                    "These seat codes do not exist on this screen: " + String.join(", ", invalid));
        }

        LocalDateTime now = LocalDateTime.now();
        List<com.moviebooking.booking.model.ShowSeat> toReserve = seatCodes.stream()
                .map(byCode::get)
                .filter(s -> s.getStatus() != com.moviebooking.booking.model.SeatStatus.BOOKED)
                .toList();

        for (com.moviebooking.booking.model.ShowSeat s : toReserve) {
            s.setStatus(com.moviebooking.booking.model.SeatStatus.BOOKED);
            // No booking record is created - these are admin-reserved seats
            // that simply never become available. If audit trail is needed,
            // a zero-value Booking could be created here.
        }
        showSeatRepository.saveAll(toReserve);
    }

    private java.math.BigDecimal cheapestTierPrice(List<TierPriceRequest> in) {
        if (in == null || in.isEmpty()) {
            throw new BusinessException(
                    "Enter a ticket price. If this screen has seat tiers, price each tier.");
        }
        return in.stream()
                .filter(p -> p != null && p.price() != null)
                .map(TierPriceRequest::price)
                .min(java.math.BigDecimal::compareTo)
                .orElseThrow(() -> new BusinessException("Enter a ticket price for each seat tier."));
    }

    private List<ShowPricingService.TierPriceInput> toPricingInputs(List<TierPriceRequest> in) {
        if (in == null) {
            return List.of();
        }
        return in.stream()
                .map(p -> new ShowPricingService.TierPriceInput(p.tierId(), p.price()))
                .collect(Collectors.toList());
    }

    public List<ShowResponse> getAllShows(String scope) {
        return showsForScope(scope).stream()
                .map(this::mapToShowResponse)
                .collect(Collectors.toList());
    }

    // Scope filter for the show list. Previously every show ever scheduled was
    // returned, so last month's completed screenings sat alongside upcoming ones.
    // "upcoming" starts at the beginning of today so a show earlier today is
    // still visible to staff rather than vanishing at its start time.
    private List<Show> showsForScope(String scope) {
        String s = (scope == null || scope.isBlank()) ? "upcoming" : scope.trim().toLowerCase();
        LocalDateTime startOfToday = java.time.LocalDate.now().atStartOfDay();

        switch (s) {
            case "past":
                return showRepository.findByIsDeletedFalseAndStartTimeLessThanOrderByStartTimeDesc(startOfToday);
            case "all":
                return showRepository.findByIsDeletedFalseOrderByStartTimeDesc();
            case "upcoming":
            default:
                // Filter to only future shows (not started yet) for customers
                // but include today's shows for admin visibility
                return showRepository
                        .findByIsDeletedFalseAndStartTimeGreaterThanEqualOrderByStartTimeAsc(LocalDateTime.now());
        }
    }


    // Touching movie/screen/theatre here (inside the @Transactional service)
    // forces the LAZY proxies to load while the Hibernate session is still open.
    private ShowResponse mapToShowResponse(Show s) {
        Movie movie = s.getMovie();
        Screen screen = s.getScreen();
        Theatre theatre = (screen != null) ? screen.getTheatre() : null;
        return new ShowResponse(
                s.getId(),
                movie != null ? movie.getId() : null,
                movie != null ? movie.getTitle() : null,
                screen != null ? screen.getId() : null,
                screen != null ? screen.getName() : null,
                theatre != null ? theatre.getName() : null,
                s.getStartTime(),
                s.getLanguage() != null ? s.getLanguage().name() : null,
                s.getFormat() != null ? s.getFormat().name() : null,
                s.getHasCaptions(),
                s.getBasePrice()
        );
    }

    // Soft delete: hard-deleting a show destroyed its bookings, and with them
    // the revenue history the Analytics module is meant to report on.
    // Past shows cannot be cancelled as they have already occurred.
    public void cancelShow(Long id) {
        Show show = showRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Show not found with ID: " + id));
        
        // Prevent cancelling past shows - they've already happened
        if (show.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(
                    "Cannot cancel a show that has already started or passed. " +
                    "Past shows cannot be cancelled.");
        }
        
        show.setIsDeleted(true);
        showRepository.save(show);
    }

    // ================= SEAT TIERS & LAYOUT (Phase 3) =================
    // Delegated to SeatConfigService with a null theatre restriction, meaning
    // unrestricted admin access. OwnerService calls the same methods with its
    // own theatre id.

    public List<SeatTierResponse> listTiers(Long screenId) {
        return seatConfig.listTiers(screenId, null).stream()
                .map(this::toTierResponse).collect(Collectors.toList());
    }

    public SeatTierResponse createTier(Long screenId, SeatTierRequest req) {
        return toTierResponse(seatConfig.createTier(
                screenId, null, req.name(), req.displayOrder(), req.colorHex()));
    }

    public SeatTierResponse updateTier(Long tierId, SeatTierRequest req) {
        return toTierResponse(seatConfig.updateTier(
                tierId, null, req.name(), req.displayOrder(), req.colorHex()));
    }

    public void deleteTier(Long tierId) {
        seatConfig.deleteTier(tierId, null);
    }

    public ScreenLayoutDetailResponse getLayoutDetail(Long screenId) {
        return buildLayoutDetail(screenId, null);
    }

    public ScreenLayoutDetailResponse saveLayout(Long screenId, LayoutSaveRequest req) {
        List<List<SeatConfigService.SeatCell>> grid = toCellGrid(req.grid());
        seatConfig.replaceLayout(screenId, null, grid);
        return buildLayoutDetail(screenId, null);
    }

    public ScreenSeatResponse assignSeatTier(Long seatId, SeatTierAssignRequest req) {
        return toSeatResponse(seatConfig.updateSeatTier(seatId, null, req.tierId()));
    }

    public List<TierPriceResponse> getShowPrices(Long showId) {
        return showPricing.listPrices(showId).stream()
                .map(p -> new TierPriceResponse(
                        p.getSeatTier().getId(),
                        p.getSeatTier().getName(),
                        p.getSeatTier().getColorHex(),
                        p.getPrice()))
                .collect(Collectors.toList());
    }

    // ---- shared mapping helpers ----

    private List<List<SeatConfigService.SeatCell>> toCellGrid(List<List<SeatCellRequest>> grid) {
        if (grid == null || grid.isEmpty()) {
            throw new BusinessException("Seat layout cannot be empty.");
        }
        List<List<SeatConfigService.SeatCell>> out = new java.util.ArrayList<>();
        for (List<SeatCellRequest> row : grid) {
            List<SeatConfigService.SeatCell> cells = new java.util.ArrayList<>();
            if (row != null) {
                for (SeatCellRequest c : row) {
                    SeatType type = SeatType.SEAT;
                    if (c != null && c.type() != null && !c.type().isBlank()) {
                        try {
                            type = SeatType.valueOf(c.type().trim().toUpperCase());
                        } catch (IllegalArgumentException e) {
                            throw new BusinessException("Unknown seat type: " + c.type());
                        }
                    }
                    cells.add(new SeatConfigService.SeatCell(
                            type, c == null ? null : c.tierId()));
                }
            }
            out.add(cells);
        }
        return out;
    }

    private ScreenLayoutDetailResponse buildLayoutDetail(Long screenId, Long restrictTo) {
        List<SeatTierResponse> tiers = seatConfig.listTiers(screenId, restrictTo).stream()
                .map(this::toTierResponse).collect(Collectors.toList());

        List<ScreenSeat> seats = seatConfig.listSeats(screenId, restrictTo);
        List<ScreenSeatResponse> seatDtos = seats.stream()
                .map(this::toSeatResponse).collect(Collectors.toList());

        int rows = seats.stream().map(ScreenSeat::getRowLabel).distinct().toList().size();
        int cols = seats.stream().mapToInt(ScreenSeat::getColIndex).max().orElse(-1) + 1;

        Screen screen = screenRepository.findByIdAndIsDeletedFalse(screenId)
                .orElseThrow(() -> new ResourceNotFoundException("Screen not found with ID: " + screenId));

        // Surfacing the lock up-front lets the designer disable editing instead of
        // letting the user draw a layout that will be rejected on save.
        long locked = showSeatRepository.countMaterializedForScreen(screenId);
        boolean editable = (locked == 0);
        String lockReason = editable ? null
                : "Shows on this screen have already opened their seat maps. "
                + "Cancel those shows to change the seating.";

        return new ScreenLayoutDetailResponse(
                screenId, screen.getName(), screen.getTotalSeats(),
                rows, cols, editable, lockReason, tiers, seatDtos);
    }

    private SeatTierResponse toTierResponse(SeatTier t) {
        return new SeatTierResponse(
                t.getId(),
                t.getScreen() != null ? t.getScreen().getId() : null,
                t.getName(), t.getDisplayOrder(), t.getColorHex(),
                screenSeatRepository.countByTierId(t.getId()));
    }

    private ScreenSeatResponse toSeatResponse(ScreenSeat s) {
        SeatTier t = s.getSeatTier();
        return new ScreenSeatResponse(
                s.getId(), s.getRowLabel(), s.getColIndex(), s.getSeatNumber(),
                s.getSeatCode(), s.getSeatType().name(),
                t != null ? t.getId() : null,
                t != null ? t.getName() : null,
                t != null ? t.getColorHex() : null);
    }

    // ===== Booking logs =====
    public List<BookingResponse> getAllBookings() {
        return bookingService.getAllBookings();
    }

    public List<BookingResponse> getBookingsByTheatre(Long theatreId) {
        return bookingService.getBookingsByTheatre(theatreId);
    }

    public List<BookingResponse> getBookingsByShow(Long showId) {
        return bookingService.getBookingsByShow(showId);
    }
}
