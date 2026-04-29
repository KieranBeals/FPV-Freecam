{ pkgs ? import <nixpkgs> { } }:

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
pkgs.mkShell {
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

  JAVA_HOME = pkgs.jdk25.home;
  GRADLE_USER_HOME = ".gradle";
  GRADLE_OPTS = "-Dorg.gradle.java.installations.paths=${pkgs.jdk21.home},${pkgs.jdk25.home}";
  LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath runtimeLibs;

  shellHook = ''
    echo "FPV Freecam shell: Java $(${pkgs.jdk25}/bin/java -version 2>&1 | head -n 1)"
    echo "Use ./gradlew build or ./gradlew runFabric1_21_11Client"
  '';
}
