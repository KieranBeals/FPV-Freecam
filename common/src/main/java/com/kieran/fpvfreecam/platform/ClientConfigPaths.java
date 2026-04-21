package com.kieran.fpvfreecam.platform;

import java.nio.file.Path;

@FunctionalInterface
public interface ClientConfigPaths {
    Path configDirectory();
}
