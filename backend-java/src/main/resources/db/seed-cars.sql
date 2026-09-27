-- Initial GearUp car fleet.
--
-- Factor XII: run as a one-off admin process:
--     java -jar target/gearup-backend.jar seed-cars
--
-- Safe to run more than once: a car whose plate number already exists is skipped.
INSERT INTO public.cars
    (plate_no, brand, model, year, category, fuel_type, color, seats, transmission, price_per_day)
VALUES
    ('ABD 1021', 'Toyota',    'Yaris',        2023, 'small', 'Petrol', 'White',  5, 'auto',   120),
    ('ABD 1022', 'Hyundai',   'Accent',       2023, 'small', 'Petrol', 'Silver', 5, 'auto',   130),
    ('ABD 1023', 'Kia',       'Pegas',        2022, 'small', 'Petrol', 'Red',    5, 'manual',  95),
    ('ABD 1024', 'Nissan',    'Sunny',        2024, 'small', 'Petrol', 'Grey',   5, 'auto',   140),
    ('ABD 1025', 'Honda',     'Civic',        2023, 'small', 'Petrol', 'Blue',   5, 'manual', 170),
    ('BKR 2031', 'Toyota',    'Camry',        2024, 'large', 'Hybrid', 'Black',  5, 'auto',   260),
    ('BKR 2032', 'Hyundai',   'Tucson',       2023, 'large', 'Petrol', 'White',  5, 'auto',   240),
    ('BKR 2033', 'Kia',       'Carnival',     2024, 'large', 'Petrol', 'Silver', 8, 'auto',   380),
    ('BKR 2034', 'Toyota',    'Land Cruiser', 2024, 'large', 'Petrol', 'White',  7, 'auto',   650),
    ('BKR 2035', 'Chevrolet', 'Tahoe',        2023, 'large', 'Petrol', 'Black',  7, 'auto',   550)
ON CONFLICT (plate_no) DO NOTHING;
