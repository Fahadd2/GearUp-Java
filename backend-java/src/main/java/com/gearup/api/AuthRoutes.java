package com.gearup.api;

import com.gearup.auth.AuthGuard;
import com.gearup.http.Router;
import com.gearup.model.ApiMessage;
import com.gearup.model.LoginRequest;
import com.gearup.model.ResetPasswordRequest;
import com.gearup.model.SignUpRequest;
import com.gearup.model.StaffLoginRequest;
import com.gearup.service.AuthService;

/**
 * {@code /auth/*}: sign-up, login, password reset and "who am I".
 */
public final class AuthRoutes {

    /** Response of {@code GET /auth/me}, same shape as the Python API. */
    record Me(String sub, String email) {
    }

    private AuthRoutes() {
    }

    public static void register(Router router, AuthService auth, AuthGuard guard) {
        router.post("/auth/staff_login", request -> auth.staffLogin(request.bodyAs(StaffLoginRequest.class)));
        router.post("/auth/signup", request -> auth.signUp(request.bodyAs(SignUpRequest.class)));
        router.post("/auth/login", request -> auth.logIn(request.bodyAs(LoginRequest.class)));

        router.post("/auth/reset_by_license", request -> {
            auth.resetPassword(request.bodyAs(ResetPasswordRequest.class));
            return new ApiMessage(true, "Password has been reset");
        });

        router.get("/auth/me", guard.anyUser((request, user) -> new Me(user.subject(), user.email())));

        // Tokens are not stored on the server (Factor VI), so logging out only means the
        // frontend deletes its copy of the token.
        router.post("/auth/logout", request -> new ApiMessage(true, "Logged out"));
    }
}
