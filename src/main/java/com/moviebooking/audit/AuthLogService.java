package com.moviebooking.audit;

import com.moviebooking.auth.entity.User;
import com.moviebooking.common.constants.AuthEventType;

public interface AuthLogService {

    void log(
            User user,
            AuthEventType eventType
    );
}