package com.kieran.fpvfreecam.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kieran.fpvfreecam.platform.ClientConfigPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneConfigMigrationTest {
    @Test
    void migratesLegacyGroupedSchemaV1ToV2(@TempDir final Path tempDir) throws Exception {
        final Path configFile = tempDir.resolve("fpv-freecam.json");
        Files.writeString(configFile, """
                {
                  "simulationMode": "CLIENT_ONLY",
                  "controller": {
                    "controllerGuid": "abc-guid",
                    "controllerName": "RadioMaster",
                    "toggleButton": 7,
                    "exitButton": 6,
                    "axisThrottle": 1,
                    "axisYaw": 0,
                    "axisPitch": 3,
                    "axisRoll": 2,
                    "axisThrottleMin": -0.8,
                    "axisThrottleMax": 0.9,
                    "invertThrottle": false,
                    "deadzone": 0.2
                  },
                  "craftProfile": {
                    "cameraAngleDeg": 32.5
                  }
                }
                """);

        final DroneConfig config = DroneConfig.load(configPaths(tempDir));

        assertEquals(DroneConfig.CURRENT_SCHEMA_VERSION, config.schemaVersion);
        assertEquals("abc-guid", config.controller.controllerGuid);
        assertEquals("RadioMaster", config.controller.controllerName);
        assertEquals(7, config.controller.armButton);
        assertEquals(6, config.controller.disarmButton);
        assertEquals(-1, config.controller.resetButton);
        assertEquals(1, config.controller.axisThrottle);
        assertEquals(0.2F, config.controller.deadzone, 1.0E-6F);
        assertEquals(32.5F, config.craftProfile.cameraAngleDeg, 1.0E-6F);
        assertEquals(1.0F, config.craftProfile.massKg, 1.0E-6F);
        assertEquals(DroneConfig.SimulationMode.CLIENT_ONLY, config.simulationMode);
        assertTrue(config.crashSettings.exitToPlayerOnDamage);

        final JsonObject saved = JsonParser.parseString(Files.readString(configFile)).getAsJsonObject();
        assertEquals(DroneConfig.CURRENT_SCHEMA_VERSION, saved.get("schemaVersion").getAsInt());
        assertTrue(saved.has("controller"));
        assertTrue(saved.has("rateProfile"));
        assertTrue(saved.has("craftProfile"));
        assertFalse(saved.getAsJsonObject("controller").has("toggleButton"));
        assertFalse(saved.getAsJsonObject("controller").has("exitButton"));
    }

    @Test
    void migratesLegacyFlatConfigAndSavesSchemaV2(@TempDir final Path tempDir) throws Exception {
        final Path configFile = tempDir.resolve("fpv-freecam.json");
        Files.writeString(configFile, """
                {
                  "controllerGuid": "abc-guid",
                  "controllerName": "RadioMaster",
                  "toggleButton": 7,
                  "exitButton": 6,
                  "axisThrottle": 1,
                  "axisYaw": 0,
                  "axisPitch": 3,
                  "axisRoll": 2,
                  "axisThrottleMin": -0.8,
                  "axisThrottleMax": 0.9,
                  "invertThrottle": false,
                  "deadzone": 0.2,
                  "cameraPitch": 32.5
                }
                """);

        final DroneConfig config = DroneConfig.load(configPaths(tempDir));

        assertEquals(DroneConfig.CURRENT_SCHEMA_VERSION, config.schemaVersion);
        assertEquals(7, config.controller.armButton);
        assertEquals(6, config.controller.disarmButton);
        assertEquals(-1, config.controller.resetButton);
    }

    @Test
    void clampsGroupedValuesOnLoad(@TempDir final Path tempDir) throws Exception {
        final Path configFile = tempDir.resolve("fpv-freecam.json");
        Files.writeString(configFile, """
                {
                  "simulationMode": "CLIENT_ONLY",
                  "controller": {
                    "deadzone": 2.0,
                    "axisThrottleMin": 1.0,
                    "axisThrottleMax": 0.0
                  },
                  "rateProfile": {
                    "rollSuperRate": 2.0,
                    "yawExpo": -1.0
                  },
                  "throttleProfile": {
                    "throttleMid": -5.0,
                    "throttleExpo": 7.0
                  },
                  "craftProfile": {
                    "cameraAngleDeg": 150.0,
                    "thrustToWeight": 0.5,
                    "massKg": 0.25
                  },
                  "realismProfile": {
                    "batterySagMaxLoss": 2.0
                  },
                  "crashSettings": {
                    "hardImpactSpeedThreshold": -1.0,
                    "exitToPlayerOnDamage": false
                  }
                }
                """);

        final DroneConfig config = DroneConfig.load(configPaths(tempDir));

        assertEquals(0.95F, config.controller.deadzone, 1.0E-6F);
        assertEquals(-1.0F, config.controller.axisThrottleMin, 1.0E-6F);
        assertEquals(1.0F, config.controller.axisThrottleMax, 1.0E-6F);
        assertEquals(0.99F, config.rateProfile.rollSuperRate, 1.0E-6F);
        assertEquals(0.0F, config.rateProfile.yawExpo, 1.0E-6F);
        assertEquals(0.05F, config.throttleProfile.throttleMid, 1.0E-6F);
        assertEquals(1.0F, config.throttleProfile.throttleExpo, 1.0E-6F);
        assertEquals(90.0F, config.craftProfile.cameraAngleDeg, 1.0E-6F);
        assertEquals(1.2F, config.craftProfile.thrustToWeight, 1.0E-6F);
        assertEquals(0.25F, config.craftProfile.massKg, 1.0E-6F);
        assertEquals(0.35F, config.realismProfile.batterySagMaxLoss, 1.0E-6F);
        assertEquals(1.0F, config.crashSettings.hardImpactSpeedThreshold, 1.0E-6F);
        assertFalse(config.crashSettings.exitToPlayerOnDamage);
    }

    @Test
    void defaultsDamageExitToTrueWhenMissing(@TempDir final Path tempDir) throws Exception {
        final Path configFile = tempDir.resolve("fpv-freecam.json");
        Files.writeString(configFile, """
                {
                  "simulationMode": "CLIENT_ONLY",
                  "controller": {
                    "armButton": 7,
                    "disarmButton": 6,
                    "resetButton": -1
                  },
                  "crashSettings": {
                    "hardImpactSpeedThreshold": 12.0
                  }
                }
                """);

        final DroneConfig config = DroneConfig.load(configPaths(tempDir));
        assertTrue(config.crashSettings.exitToPlayerOnDamage);
    }

    private static ClientConfigPaths configPaths(final Path path) {
        return () -> path;
    }
}
