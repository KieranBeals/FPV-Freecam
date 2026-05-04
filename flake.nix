{
  description = "FPV Freecam development shell and Gradle runners";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];

      forAllSystems = nixpkgs.lib.genAttrs systems;

      mkPkgs = system: import nixpkgs {
        inherit system;
      };

      mkEnv = pkgs:
        let
          runtimeLibs = with pkgs; [
            alsa-lib
            dbus
            flite
            fontconfig
            freetype
            glfw
            libGL
            libpulseaudio
            libxkbcommon
            openal
            udev
            wayland
            libx11
            libxcursor
            libxext
            libxi
            libxinerama
            libxrandr
            libxrender
            libxxf86vm
          ];
        in
        {
          packages = with pkgs; [
            jdk25
            jdk21

            bash
            coreutils
            findutils
            git
            gnugrep
            gnused
            gnumake
            pkg-config
            unzip
            which
            zip
          ] ++ runtimeLibs;

          env = {
            JAVA_HOME = pkgs.jdk25.home;
            GRADLE_USER_HOME = ".gradle";
            GRADLE_OPTS = "-Dorg.gradle.java.installations.paths=${pkgs.jdk21.home},${pkgs.jdk25.home}";
            LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath runtimeLibs;
          };
        };

      mkGradleApp = pkgs: name: args:
        let
          env = mkEnv pkgs;
        in
        {
          type = "app";
          program = toString (pkgs.writeShellScript "${name}" ''
            set -euo pipefail

            export JAVA_HOME="${env.env.JAVA_HOME}"
            export GRADLE_USER_HOME="$PWD/${env.env.GRADLE_USER_HOME}"
            export GRADLE_OPTS="${env.env.GRADLE_OPTS} ''${GRADLE_OPTS:-}"
            export LD_LIBRARY_PATH="${env.env.LD_LIBRARY_PATH}''${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
            export PATH="${pkgs.lib.makeBinPath env.packages}:$PATH"

            exec ./gradlew ${args} -Porg.gradle.java.installations.paths="${pkgs.jdk21.home},${pkgs.jdk25.home}" "$@"
          '');
        };
    in
    {
      devShells = forAllSystems (system:
        let
          pkgs = mkPkgs system;
          env = mkEnv pkgs;
        in
        {
          default = pkgs.mkShell {
            packages = env.packages;
            inherit (env) env;

            shellHook = ''
              echo "FPV Freecam shell: Java $(${pkgs.jdk25}/bin/java -version 2>&1 | head -n 1)"
              echo "Gradle toolchains: ${pkgs.jdk21.home}, ${pkgs.jdk25.home}"
              echo "Use ./gradlew build or nix run .#compile"
            '';
          };
        });

      apps = forAllSystems (system:
        let
          pkgs = mkPkgs system;
        in
        {
          compile = mkGradleApp pkgs "fpv-freecam-compile" "build";
          runFabric26_1Client = mkGradleApp pkgs "fpv-freecam-run-fabric-26-1" "runFabric26_1Client";
          runFabric1_21_11Client = mkGradleApp pkgs "fpv-freecam-run-fabric-1-21-11" "runFabric1_21_11Client";
          runNeoForgeClient = mkGradleApp pkgs "fpv-freecam-run-neoforge" "runNeoForgeClient";
        });
    };
}
