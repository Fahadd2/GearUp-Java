package com.gearup.api;

import com.gearup.auth.AuthGuard;
import com.gearup.http.Router;
import com.gearup.model.ApiMessage;
import com.gearup.model.RentalCloseRequest;
import com.gearup.model.RentalStartRequest;
import com.gearup.service.RentalService;

import java.math.BigDecimal;

/** {@code /rentals/*}: staff record when a car is picked up and returned. */
public final class RentalRoutes {

    /** Response of {@code POST /rentals/close}, same shape as the Python API. */
    record RentalClosed(boolean ok, String message, BigDecimal total) {
    }

    private RentalRoutes() {
    }

    public static void register(Router router, RentalService rentals, AuthGuard guard) {
        router.post("/rentals/start", guard.staffOnly((request, staff) -> {
            rentals.start(request.bodyAs(RentalStartRequest.class), staff);
            return new ApiMessage(true, "Rental started");
        }));

        router.post("/rentals/close", guard.staffOnly((request, staff) -> {
            BigDecimal total = rentals.close(request.bodyAs(RentalCloseRequest.class), staff);
            return new RentalClosed(true, "Rental closed", total);
        }));
    }
}
