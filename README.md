100% client-side FPV freecam simulation tuned as a 5-inch freestyle quad.

No drone packets are sent. The drone state does not move any server-authoritative entity.
This is intended for servers that already allow freecam-style detached camera behavior.
It is not an anti-cheat bypass.

## Features
- Client-only fixed-step FPV simulation (`240 Hz`) with acro-only rates
- Betaflight-style `RC rate / super rate / expo`
- Throttle curve, motor lag, anisotropic drag, battery sag, and descent wash
- Separate controller actions for `Arm`, `Disarm`, and `Reset-to-Player`
- Crash model with `EXIT_TO_PLAYER`, `QUICK_REARM`, and `CHECKPOINT_RESPAWN`
- Four setup pages: `Controller`, `Rates`, `Craft`, `Realism & Crash`
- Axis/button capture, calibration, invert options, and deadzone tuning
- In-flight camera angle adjustment (optional)
- Global active-flight exit triggers: `Esc`, movement keybind press, and optional damage edge detection

## In-Game Setup
1. Open the setup screen from Mod Menu.
2. `Controller` page: select controller, bind `Arm/Disarm/Reset`, bind axes, set deadzone/invert.
3. `Rates` page: tune roll/pitch/yaw `RC/super/expo`.
4. `Craft` page: tune camera angle, throttle curve, thrust, drag, and response; `Mass` changes rotational/translational inertia and adds a small high-speed float offset.
5. `Realism & Crash` page: tune sag/wash, choose crash reset mode, and toggle `Exit To Player On Damage`.
6. Active control behavior:
   - `Arm` enters FPV when inactive, and re-arms motors while detached when currently disarmed.
   - `Disarm` cuts motors but keeps the detached camera active; momentum and rotation continue naturally.
   - `Reset` exits back to the player camera.
