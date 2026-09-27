package com.gearup.api;

import com.gearup.auth.AuthGuard;
import com.gearup.http.Router;
import com.gearup.model.PaymentRequest;
import com.gearup.service.PaymentService;

/** {@code POST /payments/pay}: staff record a payment against an invoice. */
public final class PaymentRoutes {

    private PaymentRoutes() {
    }

    public static void register(Router router, PaymentService payments, AuthGuard guard) {
        router.post("/payments/pay", guard.staffOnly((request, staff) ->
                payments.pay(request.bodyAs(PaymentRequest.class), staff)));
    }
}
