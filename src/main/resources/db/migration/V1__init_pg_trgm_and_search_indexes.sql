CREATE EXTENSION IF NOT EXISTS pg_trgm;

DO $$
BEGIN
    IF to_regclass('public.university') IS NOT NULL THEN
        EXECUTE '
            CREATE INDEX IF NOT EXISTS idx_university_name_trgm
            ON university
            USING GIN (name gin_trgm_ops)
        ';
    END IF;

    IF to_regclass('public.university_department') IS NOT NULL THEN
        EXECUTE '
            CREATE INDEX IF NOT EXISTS idx_university_department_name_trgm
            ON university_department
            USING GIN (department gin_trgm_ops)
        ';

        EXECUTE '
            CREATE INDEX IF NOT EXISTS idx_university_department_university_id
            ON university_department (university_id)
        ';
    END IF;
END $$;
