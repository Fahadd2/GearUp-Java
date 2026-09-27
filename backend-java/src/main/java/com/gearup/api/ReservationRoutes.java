package com.gearup.api;

import com.gearup.auth.AuthGuard;
import com.gearup.exception.BadRequestException;
import com.gearup.http.Router;
import com.gearup.model.ApiMessage;
import com.gearup.model.BookingRequest;
import com.gearup.model.ReservationStatus;
import com.gearup.model.ReservationStatusUpdate;
import com.gearup.service.BookingService;

/**
 * {@code /reservations/*}: customers book cars and see their own bookings;
 * staff see and manage all bookings.
 */
public final class ReservationRoutes {

    /** Response of {@code POST /reservations/auto_update_statuses}, same shape as the Python API. */
    record ExpiredUpdate(boolean ok, int updated, String message) {
    }

    private ReservationRoutes() {
    }

    public static void register(Router router, BookingService bookings, AuthGuard guard) {
        // Customers
        router.post("/reservations/create_auth", guard.customerOnly((request, customer) ->
                bookings.book(customer, request.bodyAs(BookingRequest.class))));

        router.get("/reservations/my_reservations", guard.customerOnly((request, customer) ->
                bookings.listFor(customer)));

        // Staff
        router.get("/reservations", guard.staffOnly((request, staff) -> bookings.listAll()));

        router.post("/reservations/auto_update_statuses", guard.staffOnly((request, staff) -> {
            int closed = bookings.closeExpired(staff);
            return new ExpiredUpdate(true, closed, "Updated " + closed + " expired reservation(s)");
        }));

        router.put("/reservations/{id}", guard.staffOnly((request, staff) -> {
            ReservationStatus status = request.bodyAs(ReservationStatusUpdate.class).status();
            if (status == null) {
                throw new BadRequestException("status is required");
            }
            bookings.updateStatus(request.pathParam("id"), status, staff);
            return new ApiMessage(true, "Reservation status updated to " + status.label());
        }));
    }
}
