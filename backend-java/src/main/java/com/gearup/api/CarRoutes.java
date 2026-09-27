package com.gearup.api;

import com.gearup.auth.AuthGuard;
import com.gearup.http.Request;
import com.gearup.http.Router;
import com.gearup.model.CarCategory;
import com.gearup.model.CarFilter;
import com.gearup.model.CarUpdate;
import com.gearup.model.LabeledEnum;
import com.gearup.model.Transmission;
import com.gearup.service.CarService;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code /cars}: public car search with optional filters, e.g.
 * {@code /cars?category=small&transmission=auto&start_date=2025-11-10&end_date=2025-11-12},
 * and a staff-only route to edit a car.
 */
public final class CarRoutes {

    /** Response of {@code PUT /cars/{id}}, same shape as the Python API. */
    record CarUpdated(String message, String carId) {
    }

    private CarRoutes() {
    }

    public static void register(Router router, CarService cars, AuthGuard guard) {
        router.get("/cars", request -> cars.findCars(filterFrom(request)));

        router.put("/cars/{id}", guard.staffOnly((request, staff) -> {
            String carId = request.pathParam("id");
            cars.updateCar(carId, request.bodyAs(CarUpdate.class), staff.empId());
            return new CarUpdated("Car updated successfully", carId);
        }));
    }

    private static CarFilter filterFrom(Request request) {
        return new CarFilter(
                request.queryParam("category", label -> LabeledEnum.fromLabel(CarCategory.class, label)).orElse(null),
                request.queryParam("seats", Integer::parseInt).orElse(null),
                request.queryParam("transmission", label -> LabeledEnum.fromLabel(Transmission.class, label)).orElse(null),
                request.queryParam("min_price", BigDecimal::new).orElse(null),
                request.queryParam("max_price", BigDecimal::new).orElse(null),
                request.queryParam("start_date", LocalDate::parse).orElse(null),
                request.queryParam("end_date", LocalDate::parse).orElse(null));
    }
}
