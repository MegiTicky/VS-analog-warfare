package com.erika.vsanalogwarfare.mixin;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class VsawMixinConfigPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("VSAW");
    private static final Map<String, String> TESTED_VERSIONS = Map.of(
            "valkyrien_mod", "0.1.3",
            "drivebywire", "0.0.6b",
            "trackwork", "1.0.2c"
    );
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    @Override
    public void onLoad(String mixinPackage) {
        MixinExtrasBootstrap.init();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String modId = requiredMod(mixinClassName);
        if (modId == null) return true;
        ModFileInfo file = LoadingModList.get().getModFileById(modId);
        if (file == null) return false;
        warnUntestedVersion(modId);
        return true;
    }

    private static String requiredMod(String mixinClassName) {
        if (mixinClassName.contains(".mixin.compat.Vmod")) return "valkyrien_mod";
        if (mixinClassName.contains(".mixin.compat.Dbw")) return "drivebywire";
        if (mixinClassName.contains(".mixin.compat.Trackwork")) return "trackwork";
        return null;
    }

    private static void warnUntestedVersion(String modId) {
        if (!WARNED.add(modId)) return;
        String installed = installedVersion(modId);
        String tested = TESTED_VERSIONS.get(modId);
        if (installed == null || tested == null || installed.equals(tested)
                || installed.startsWith(tested + "-") || installed.startsWith(tested + "+")) return;
        LOGGER.warn("[VSAW] {} is installed as {} but was validated against {}. Vehicle setup integrations "
                + "may not work; update the mod or disable the affected feature.", modId, installed, tested);
    }

    private static String installedVersion(String modId) {
        ModFileInfo file = LoadingModList.get().getModFileById(modId);
        if (file == null || file.getMods().isEmpty()) return null;
        try { return file.getMods().get(0).getVersion().toString(); }
        catch (Throwable ignored) { return null; }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
