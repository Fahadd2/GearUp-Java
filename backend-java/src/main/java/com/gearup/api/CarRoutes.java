package com.gearup.api;

import com.gearup.http.Request;
import com.gearup.http.Router;
import com.gearup.model.CarCategory;
import com.gearup.model.CarFilter;
import com.gearup.model.LabeledEnum;
import com.gearup.model.Transmission;
import com.gearup.service.CarService;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * {@code GET /cars}: public car search with optional filters, e.g.
 * {@code /cars?category=small&transmission=auto&start_date=2025-11-10&end_date=2025-11-12}.
 */
public final class CarRoutes {

    private CarRoutes() {
    }

    public static void register(Router router, CarService cars) {
        router.get("/cars", request -> cars.findCars(filterFrom(request)));
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
