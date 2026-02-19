@echo off
REM =============================================================
REM  Nabat - PostgreSQL Database Setup Script (Windows)
REM  Creates database nabat_db and user nabat_user
REM =============================================================

echo Nabat Database Setup
echo ====================

REM Check if psql is available
where psql >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: psql not found on PATH.
    echo Please install PostgreSQL 16 and add its bin directory to your PATH.
    echo Download: https://www.postgresql.org/download/windows/
    echo Typical path: C:\Program Files\PostgreSQL\16\bin
    exit /b 1
)

echo Found psql. Running setup-db.sql...
echo.

REM Run the setup SQL as the postgres superuser
psql -U postgres -f setup-db.sql
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Database setup failed.
    echo Common causes:
    echo   - PostgreSQL service is not running
    echo   - The postgres superuser password is required ^(you may be prompted^)
    echo   - Port 5432 is blocked or in use
    echo.
    echo See LOCAL_DB_SETUP.md for troubleshooting steps.
    exit /b 1
)

echo.
echo SUCCESS: Database setup complete.
echo   Database : nabat_db
echo   User     : nabat_user
echo   Password : nabat_password
echo.
echo To verify the connection, run:
echo   psql -U nabat_user -d nabat_db -h localhost
