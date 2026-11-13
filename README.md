# PAD LINK (Android, Kotlin)
Phone controller for a PS3 running **webMAN MOD** (Cobra). It sends the official
webMAN web command `/pad.ps3?<buttons>` over HTTP (port 80) - no extra plugin needed.
Push to GitHub: Actions builds the APK. Download: Actions > latest run > Artifacts.

Test first from the phone browser (replace the IP):
1. http://PS3-IP/pad.ps3?cross                  -> presses X
2. http://PS3-IP/pad.ps3?down_hold  then  http://PS3-IP/pad.ps3?release
3. http://PS3-IP/pad.ps3?release;/pad.ps3?up_cross_hold   (multi-command form)
