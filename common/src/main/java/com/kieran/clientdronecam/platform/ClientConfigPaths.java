package com.kieran.clientdronecam.platform;

import java.nio.file.Path;

@FunctionalInterface
public interface ClientConfigPaths {
    Path configDirectory();
}
