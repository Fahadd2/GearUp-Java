package com.gearup.model;

/** Body of {@code POST /auth/reset_by_license}. */
public record ResetPasswordRequest(String email, String licenseNo, String newPassword) {

    /** Keeps the password out of logs. */
    @Override
    public String toString() {
        return "ResetPasswordRequest[email=" + email + ", licenseNo=" + licenseNo + ", newPassword=***]";
    }
}
