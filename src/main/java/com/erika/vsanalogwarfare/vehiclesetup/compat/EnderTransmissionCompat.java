package com.erika.vsanalogwarfare.vehiclesetup.compat;

import com.erika.vsanalogwarfare.VSAnalogWarfare;
import com.erika.vsanalogwarfare.vehiclesetup.VehicleSetupAction;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.Locale;

public final class EnderTransmissionCompat {
    public static final String MOD_ID = "createendertransmission";
    public static final String ORIGINAL_PASSWORD_TAG = "VSAWOriginalEnderPassword";
    public static final String ORIGINAL_CHANNEL_TAG = "VSAWOriginalEnderChannel";
    public static final String REMAPPED_TAG = "VSAWEnderRemapped";
    public static final int PASSWORD_LIMIT = 32;

    private static final String ENERGY_TRANSMITTER_ID = MOD_ID + ":energy_transmitter";
    private static final SecureRandom RANDOM = new SecureRandom();

    private EnderTransmissionCompat() { }

    public static boolean isEnergyTransmitter(BlockState state) {
        return ENERGY_TRANSMITTER_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    public static boolean isEnergyTransmitter(BlockEntity blockEntity) {
        return blockEntity instanceof KineticBlockEntity
                && isEnergyTransmitter(blockEntity.getBlockState());
    }

    public static String newPlacementId() {
        long value = RANDOM.nextLong() & ((1L << 40) - 1);
        return String.format(Locale.ROOT, "%8s", Long.toString(value, 36))
                .replace(' ', '0').toUpperCase(Locale.ROOT);
    }

    public static String runtimePassword(String originalPassword, int channel, String placementId) {
        String suffix = "-" + placementId + groupId(originalPassword, channel);
        int prefixLength = Math.min(16, PASSWORD_LIMIT - suffix.length());
        String prefix = originalPassword.substring(0, Math.min(prefixLength, originalPassword.length()));
        return prefix + suffix;
    }

    @Nullable
    public static String configure(Level level, BlockPos pos, VehicleSetupAction action,
                                   @Nullable String placementId) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof KineticBlockEntity transmitter)
                || !isEnergyTransmitter(blockEntity)) {
            return "recorded Ender energy transmitter could not be found";
        }
        if (placementId == null || placementId.isEmpty()) {
            if (transmitter.getPersistentData().getBoolean(REMAPPED_TAG)) return null;
            return "Ender transmitter remapping requires a VMod placement";
        }
        String originalPassword = action.transmitterPassword();
        if (originalPassword == null) return "recorded Ender transmitter password is missing";
        String password = runtimePassword(originalPassword, action.transmitterChannel(), placementId);
        try {
            refresh(transmitter, action.transmitterChannel(), password);
            transmitter.getPersistentData().putString(ORIGINAL_PASSWORD_TAG, originalPassword);
            transmitter.getPersistentData().putInt(ORIGINAL_CHANNEL_TAG, action.transmitterChannel());
            transmitter.getPersistentData().putBoolean(REMAPPED_TAG, true);
            transmitter.setChanged();
            level.sendBlockUpdated(pos, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
            return null;
        } catch (ReflectiveOperationException | LinkageError error) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] Could not refresh Ender transmitter at {}", pos, error);
            return "Ender transmitter network refresh failed";
        }
    }

    @Nullable
    public static String pair(Level level, BlockPos sourcePos, BlockPos targetPos) {
        BlockEntity sourceEntity = level.getBlockEntity(sourcePos);
        BlockEntity targetEntity = level.getBlockEntity(targetPos);
        if (!(sourceEntity instanceof KineticBlockEntity source) || !isEnergyTransmitter(sourceEntity)) {
            return "selected source is not an Ender energy transmitter";
        }
        if (!(targetEntity instanceof KineticBlockEntity target) || !isEnergyTransmitter(targetEntity)) {
            return "selected target is not an Ender energy transmitter";
        }
        int channel = source.getPersistentData().getInt("channel");
        String password = source.getPersistentData().getString("password");
        if (password.isEmpty()) return "selected source has no configured password";
        try {
            refresh(target, channel, password);
            target.getPersistentData().remove(ORIGINAL_PASSWORD_TAG);
            target.getPersistentData().remove(ORIGINAL_CHANNEL_TAG);
            target.getPersistentData().remove(REMAPPED_TAG);
            target.setChanged();
            level.sendBlockUpdated(targetPos, targetEntity.getBlockState(), targetEntity.getBlockState(), 3);
            return null;
        } catch (ReflectiveOperationException | LinkageError error) {
            VSAnalogWarfare.LOGGER.warn("[VSAW] Could not pair Ender transmitters at {} and {}",
                    sourcePos, targetPos, error);
            return "Ender transmitter pairing refresh failed";
        }
    }

    private static String groupId(String password, int channel) {
        int hash = 0x811C9DC5 ^ channel;
        for (int index = 0; index < password.length(); index++) {
            hash ^= password.charAt(index);
            hash *= 0x01000193;
        }
        return String.format(Locale.ROOT, "%7s", Integer.toUnsignedString(hash, 36))
                .replace(' ', '0').toUpperCase(Locale.ROOT);
    }

    private static void refresh(KineticBlockEntity transmitter, int channel, String password)
            throws ReflectiveOperationException {
        invokeNoArgs(transmitter, "reloadSettings");
        transmitter.getPersistentData().putInt("channel", channel);
        transmitter.getPersistentData().putString("password", password);
        transmitter.detachKinetics();
        transmitter.attachKinetics();
        invokeNoArgs(transmitter, "afterReload");
    }

    private static void invokeNoArgs(Object target, String name) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name);
        try {
            method.invoke(target);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof LinkageError linkageError) throw linkageError;
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw exception;
        }
    }
}
