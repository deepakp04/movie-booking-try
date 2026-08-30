package com.moviebooking.audit;

import com.moviebooking.auth.entity.User;

import com.moviebooking.common.constants.AuthEventType;
import org.springframework.stereotype.Service;




@Service
public class AuthLogServiceImpl
        implements AuthLogService {

    private final AuthLogRepository repository;
    
  

    public AuthLogServiceImpl(
            AuthLogRepository repository) {

        this.repository = repository;
       
    }

    @Override
    public void log(
            User user,
            AuthEventType eventType) {

        AuthLog log =
                new AuthLog();

        log.setUser(user);

        log.setEventType(eventType);

        repository.save(log);
    }
}