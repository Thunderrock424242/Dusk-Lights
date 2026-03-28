package com.thunder.dusklights;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class LinkedLightsSavedData extends SavedData {
    private static final String DATA_NAME = DuskLights.MOD_ID + "_linked_lights";
    private static final String LINKED_LIGHTS_KEY = "linked_lights";
    private static final String SCANNED_CHUNKS_KEY = "scanned_chunks";
    private static final String PENDING_CHUNK_SCANS_KEY = "pending_chunk_scans";

    private final Set<Long> linkedLightPositions = new HashSet<>();
    private final Set<Long> scannedChunks = new HashSet<>();
    private final ArrayDeque<Long> pendingChunkScans = new ArrayDeque<>();
    private final Set<Long> pendingChunkScanSet = new HashSet<>();

    private static final SavedData.Factory<LinkedLightsSavedData> FACTORY = new SavedData.Factory<>(
            LinkedLightsSavedData::new,
            LinkedLightsSavedData::load,
            DataFixTypes.LEVEL
    );

    public static LinkedLightsSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static LinkedLightsSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        LinkedLightsSavedData data = new LinkedLightsSavedData();

        ListTag linkedLights = tag.getList(LINKED_LIGHTS_KEY, Tag.TAG_LONG);
        for (Tag entry : linkedLights) {
            data.linkedLightPositions.add(((LongTag) entry).getAsLong());
        }

        ListTag chunks = tag.getList(SCANNED_CHUNKS_KEY, Tag.TAG_LONG);
        for (Tag entry : chunks) {
            data.scannedChunks.add(((LongTag) entry).getAsLong());
        }

        ListTag pendingScans = tag.getList(PENDING_CHUNK_SCANS_KEY, Tag.TAG_LONG);
        for (Tag entry : pendingScans) {
            long packedChunkPos = ((LongTag) entry).getAsLong();
            data.pendingChunkScans.addLast(packedChunkPos);
            data.pendingChunkScanSet.add(packedChunkPos);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag linkedLights = new ListTag();
        for (Long packedPos : linkedLightPositions) {
            linkedLights.add(LongTag.valueOf(packedPos));
        }
        tag.put(LINKED_LIGHTS_KEY, linkedLights);

        ListTag chunks = new ListTag();
        for (Long packedChunkPos : scannedChunks) {
            chunks.add(LongTag.valueOf(packedChunkPos));
        }
        tag.put(SCANNED_CHUNKS_KEY, chunks);

        ListTag pendingScans = new ListTag();
        for (Long packedChunkPos : pendingChunkScans) {
            pendingScans.add(LongTag.valueOf(packedChunkPos));
        }
        tag.put(PENDING_CHUNK_SCANS_KEY, pendingScans);

        return tag;
    }

    public boolean isLinked(BlockPos pos) {
        return linkedLightPositions.contains(pos.asLong());
    }

    public boolean toggleLinked(BlockPos pos) {
        long packedPos = pos.asLong();
        boolean linked;
        if (linkedLightPositions.contains(packedPos)) {
            linkedLightPositions.remove(packedPos);
            linked = false;
        } else {
            linkedLightPositions.add(packedPos);
            linked = true;
        }
        setDirty();
        return linked;
    }

    public boolean addLinked(BlockPos pos) {
        boolean added = linkedLightPositions.add(pos.asLong());
        if (added) {
            setDirty();
        }
        return added;
    }

    public boolean removeLinked(BlockPos pos) {
        boolean removed = linkedLightPositions.remove(pos.asLong());
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public Set<Long> getLinkedLightPositions() {
        return linkedLightPositions;
    }

    public boolean queueChunkScan(long packedChunkPos) {
        if (scannedChunks.contains(packedChunkPos) || pendingChunkScanSet.contains(packedChunkPos)) {
            return false;
        }

        pendingChunkScans.addLast(packedChunkPos);
        pendingChunkScanSet.add(packedChunkPos);
        setDirty();
        return true;
    }

    public Long pollPendingChunkScan() {
        Long packedChunkPos = pendingChunkScans.pollFirst();
        if (packedChunkPos != null) {
            pendingChunkScanSet.remove(packedChunkPos);
            setDirty();
        }
        return packedChunkPos;
    }

    public boolean isChunkScanned(long packedChunkPos) {
        return scannedChunks.contains(packedChunkPos);
    }

    public void markChunkScanned(long packedChunkPos) {
        if (scannedChunks.add(packedChunkPos)) {
            setDirty();
        }
    }
}
