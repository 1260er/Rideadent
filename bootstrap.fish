#!/usr/bin/env fish
# In deinem Terminal ausführen; verändert nur das übergebene Rideadent-Projekt.
set -l dir (cd (dirname (status filename)); and pwd)
if not test -d "$dir/.git"
    git -C "$dir" init -b main; or return 1
    git -C "$dir" add .; or return 1
    git -C "$dir" commit -m 'Initial Rideadent feasibility prototype'; or return 1
    git -C "$dir" switch -c dev/feasibility; or return 1
end
if command -q gradle
    gradle -p "$dir" testDebugUnitTest lintDebug assembleDebug assembleRelease
else
    echo 'Gradle nicht im PATH: Projekt zuerst in Android Studio synchronisieren.'
end
git -C "$dir" status --short --branch
git -C "$dir" log -1 --oneline
