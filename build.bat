@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem ============================================================
rem  Build Vespera Client and copy jar to F:\usermods\1.21.11
rem  (old launcher-client-*.jar is removed automatically)
rem ============================================================

set "GRADLE_VER=9.2.1"
set "GRADLE_HOME_LOCAL=%~dp0.gradle-dist\gradle-%GRADLE_VER%"

rem ---- Java 21 (Eclipse Temurin) ----
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JAVA_HOME=%%~fD"
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo [!] JDK 21 not found.
    echo     Install Temurin JDK 21 from https://adoptium.net and run again.
    pause
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [*] Java: %JAVA_HOME%

rem ---- Gradle: reuse the copy from Desktop\LauncherClient if it exists ----
set "OLD_GRADLE=%USERPROFILE%\Desktop\LauncherClient\.gradle-dist\gradle-%GRADLE_VER%"
if not exist "%GRADLE_HOME_LOCAL%\bin\gradle.bat" if exist "%OLD_GRADLE%\bin\gradle.bat" set "GRADLE_HOME_LOCAL=%OLD_GRADLE%"

rem ---- Gradle (downloaded once into .gradle-dist) ----
if not exist "%GRADLE_HOME_LOCAL%\bin\gradle.bat" (
    echo [*] Downloading Gradle %GRADLE_VER% ...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "$ProgressPreference='SilentlyContinue';" ^
      "Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GRADLE_VER%-bin.zip' -OutFile \"$env:TEMP\gradle-%GRADLE_VER%.zip\";" ^
      "Expand-Archive \"$env:TEMP\gradle-%GRADLE_VER%.zip\" '%~dp0.gradle-dist' -Force"
    if not exist "%GRADLE_HOME_LOCAL%\bin\gradle.bat" (
        echo [!] Could not download Gradle. Check your internet connection.
        pause
        exit /b 1
    )
)

rem ---- Build (build -> deployToLauncher copies jar automatically) ----
echo [*] Building... first build downloads Minecraft and takes a few minutes.
call "%GRADLE_HOME_LOCAL%\bin\gradle.bat" build --no-daemon > build.log 2>&1
set "RC=%ERRORLEVEL%"
type build.log
if not "%RC%"=="0" (
    echo.
    echo [!] BUILD FAILED. The log is saved in build.log - tell Claude, it can read it.
    pause
    exit /b 1
)

echo.
echo [OK] Done. Jar copied to F:\usermods\1.21.11
echo      Start the game from the launcher and press Right Shift.
pause
