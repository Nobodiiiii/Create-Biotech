package com.nobodiiiii.createbiotech.foundation.render.material.palette;

import com.nobodiiiii.createbiotech.foundation.render.material.palette.MaterialDiscovery.MaterialValidationReport;
import com.nobodiiiii.createbiotech.foundation.render.material.palette.MaterialDiscovery.TagSnapshot;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Owns independent server/client palette snapshots and their synchronization. */
public final class MaterialPaletteSync {
    public static final String PROTOCOL = "1";
    private static final Logger LOGGER = LogUtils.getLogger();
    // null means the server has not loaded tags; an active empty palette is distinct.
    private static final AtomicReference<MaterialPalette> SERVER = new AtomicReference<>();
    private static final AtomicReference<MaterialPalette> CLIENT = new AtomicReference<>(MaterialPalette.empty());

    private MaterialPaletteSync() { }

    public static Optional<MaterialPalette> serverPalette() {
        return Optional.ofNullable(SERVER.get());
    }

    public static void activate(MaterialPalette palette) {
        SERVER.set(Objects.requireNonNull(palette, "palette"));
    }

    public static void deactivate() {
        SERVER.set(null);
    }

    public static MaterialPalette clientPalette() {
        return CLIENT.get();
    }

    public static void replaceClient(MaterialPalette palette) {
        CLIENT.set(Objects.requireNonNull(palette, "palette"));
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL).playToClient(
                MaterialPalettePayload.TYPE,
                MaterialPalettePayload.STREAM_CODEC,
                (payload, context) -> replaceClient(payload.toPalette()));
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        TagSnapshot snapshot = MaterialDiscovery.snapshot();
        SyncCause cause = event.getPlayer() == null
                ? SyncCause.RELOAD
                : SyncCause.PLAYER_JOIN;
        SyncDecision decision = rebuild(snapshot, cause);
        if (cause == SyncCause.RELOAD && decision.broadcast()) {
            decision.report().issues().forEach(issue -> LOGGER.warn(
                    "Skipping invalid #create:casing material {}: {}", issue.id(), issue.code()));
        }
        if (!decision.broadcast()) {
            return;
        }
        MaterialPalettePayload payload = MaterialPalettePayload.from(decision.palette());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    public static SyncDecision rebuild(TagSnapshot snapshot, SyncCause cause) {
        MaterialValidationReport report = MaterialDiscovery.validate(
                snapshot.items(), snapshot.blocks(), snapshot.itemTagIds(), snapshot.blockTagIds());
        MaterialPalette next = MaterialPalette.from(report.validIds());
        boolean changed = serverPalette()
                .map(current -> !Arrays.equals(current.fingerprint(), next.fingerprint()))
                .orElse(true);
        activate(next);
        return new SyncDecision(next, report, cause == SyncCause.PLAYER_JOIN || changed);
    }

    public enum SyncCause {
        RELOAD,
        PLAYER_JOIN
    }

    public record SyncDecision(
            MaterialPalette palette,
            MaterialValidationReport report,
            boolean broadcast) {
    }
}
