100% client-side FPV freecam simulation tuned as a 5-inch freestyle quad.

No drone packets are sent. The drone state does not move any server-authoritative entity.
This is intended for servers that already allow freecam-style detached camera behavior.
It is not an anti-cheat bypass.

## Features
- Client-only fixed-step FPV simulation (`240 Hz`) with acro-only rates
- Betaflight-style `RC rate / super rate / expo`
- Throttle curve, motor lag, anisotropic drag, battery sag, and descent wash
- Crash model with `EXIT_TO_PLAYER`, `QUICK_REARM`, and `CHECKPOINT_RESPAWN`
- Four setup pages: `Controller`, `Rates`, `Craft`, `Realism & Crash`
- Axis/button capture, calibration, invert options, and deadzone tuning
- In-flight camera angle adjustment (optional)

## In-Game Setup
1. Open the setup screen from Mod Menu.
2. `Controller` page: select controller, bind `Toggle/Exit`, bind axes, set deadzone/invert.
3. `Rates` page: tune roll/pitch/yaw `RC/super/expo`.
4. `Craft` page: tune camera angle, throttle curve, thrust, drag, and response.
5. `Realism & Crash` page: tune sag/wash and choose crash reset mode.
6. Activate FPV with your configured toggle button.
