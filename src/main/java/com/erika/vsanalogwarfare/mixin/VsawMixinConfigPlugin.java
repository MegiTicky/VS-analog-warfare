package com.erika.vsanalogwarfare.mixin;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;
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
            "trackwork", "1.0.2c",
            "createendertransmission", "2.0.7-1.20.1"
    );
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /**
     * Some VS2 2.3 fork builds (tt22x2 and later, plus upstream) ship their own ship-schematic
     * mixins under mod_compat.create.client. They target the same call sites we do, and their
     * @Redirects consume those call sites first — our duplicates then fail injection and, with
     * defaultRequire=1, abort Create's class transform. On those builds VS2 provides the identical
     * feature, so ours must not apply.
     */
    private static final String VS2_COMPAT_CLIENT_PREFIX =
            "org/valkyrienskies/mod/mixin/mod_compat/create/client/";
    private static final Set<String> VS2_SCHEMATIC_MIXIN_SUFFIXES = Set.of(
            ".mixin.client.MixinDeployTool",
            ".mixin.client.MixinSchematicToolBase",
            ".mixin.client.MixinSchematicTransformation"
    );
    private static Boolean vs2ShipsSchematicMixins;

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
        if (isSchematicMixin(mixinClassName) && vs2ShipsSchematicMixins()) {
            LOGGER.info("[VSAW] Skipping {} - the installed Valkyrien Skies build ships its own "
                    + "ship-schematic mixins; the VS2 implementation will be used.", mixinClassName);
            return false;
        }
        String modId = requiredMod(mixinClassName);
        if (modId == null) return true;
        ModFileInfo file = LoadingModList.get().getModFileById(modId);
        if (file == null) return false;
        warnUntestedVersion(modId);
        return true;
    }

    private static boolean isSchematicMixin(String mixinClassName) {
        for (String suffix : VS2_SCHEMATIC_MIXIN_SUFFIXES) {
            if (mixinClassName.endsWith(suffix)) return true;
        }
        return false;
    }

    private static boolean vs2ShipsSchematicMixins() {
        Boolean cached = vs2ShipsSchematicMixins;
        if (cached != null) return cached;
        boolean found = false;
        ModFileInfo vs2 = LoadingModList.get().getModFileById("valkyrienskies");
        if (vs2 != null && vs2.getFile() != null) {
            Path path = vs2.getFile().getFilePath();
            try (ZipFile zip = new ZipFile(path.toFile())) {
                found = zip.getEntry(VS2_COMPAT_CLIENT_PREFIX + "MixinSchematicTransformation.class") != null
                        || zip.getEntry(VS2_COMPAT_CLIENT_PREFIX + "MixinSchematicToolBase.class") != null
                        || zip.getEntry(VS2_COMPAT_CLIENT_PREFIX + "MixinDeployTool.class") != null;
            } catch (Exception e) {
                LOGGER.warn("[VSAW] Could not inspect the Valkyrien Skies jar ({}); assuming it does "
                        + "not ship ship-schematic mixins.", e.toString());
            }
        }
        vs2ShipsSchematicMixins = found;
        return found;
    }

    private static String requiredMod(String mixinClassName) {
        if (mixinClassName.contains(".mixin.compat.Vmod")) return "valkyrien_mod";
        if (mixinClassName.contains(".mixin.compat.Dbw")) return "drivebywire";
        if (mixinClassName.contains(".mixin.compat.Trackwork")) return "trackwork";
        if (mixinClassName.contains("EnderTransmission") || mixinClassName.contains("EnderTransmitter")) {
            return "createendertransmission";
        }
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
