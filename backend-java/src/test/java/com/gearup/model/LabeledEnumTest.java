package com.gearup.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LabeledEnumTest {

    @Test
    void findsConstantByDatabaseLabel() {
        assertEquals(CarStatus.AVAILABLE, LabeledEnum.fromLabel(CarStatus.class, "Available"));
        assertEquals(CarCategory.SMALL, LabeledEnum.fromLabel(CarCategory.class, "small"));
        assertEquals(StaffRole.ADMIN, LabeledEnum.fromLabel(StaffRole.class, "admin"));
    }

    @Test
    void unknownLabelListsAllowedValues() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LabeledEnum.fromLabel(PaymentStatus.class, "refunded"));
        assertEquals("'refunded' is not one of: unpaid, partial, paid", e.getMessage());
    }

    @Test
    void labelsAreCaseSensitiveLikeThePostgresEnums() {
        assertThrows(IllegalArgumentException.class, () -> LabeledEnum.fromLabel(CarStatus.class, "available"));
    }

    @Test
    void nullableLabelGivesNull() {
        assertNull(LabeledEnum.fromNullableLabel(PaymentStatus.class, null));
    }

    /** Same car status rules as the Python PUT /reservations/{id} handler. */
    @Test
    void reservationStatusDecidesCarStatus() {
        assertEquals(CarStatus.RESERVED, ReservationStatus.RESERVED.carStatus());
        assertEquals(CarStatus.RENTED, ReservationStatus.ACTIVE.carStatus());
        assertEquals(CarStatus.AVAILABLE, ReservationStatus.COMPLETED.carStatus());
        assertEquals(CarStatus.AVAILABLE, ReservationStatus.CANCELLED.carStatus());
    }
}
