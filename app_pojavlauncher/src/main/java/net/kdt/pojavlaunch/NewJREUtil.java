package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Architecture.archAsString;

import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.MathUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class NewJREUtil {
    private static final int[] DOWNLOADABLE_JAVA_MAJORS = {8, 17, 21, 25};
    private static final String RUNTIME_RELEASE_BASE =
            "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/";

    private static boolean checkInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime) {
        String launcher_runtime_version;
        String installed_runtime_version = MultiRTUtils.readInternalRuntimeVersion(internalRuntime.name);
        try {
            launcher_runtime_version = Tools.read(assetManager.open(internalRuntime.path+"/version"));
        }catch (IOException exc) {
            //we don't have a runtime included!
            //if we have one installed -> return true -> proceed (no updates but the current one should be functional)
            //if we don't -> return false -> Cannot find compatible Java runtime
            return installed_runtime_version != null;
        }
        // this implicitly checks for null, so it will unpack the runtime even if we don't have one installed
        if(!launcher_runtime_version.equals(installed_runtime_version))
            return unpackInternalRuntime(assetManager, internalRuntime, launcher_runtime_version);
        else return true;
    }

    private static boolean unpackInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime, String version) {
        try {
            MultiRTUtils.installRuntimeNamedBinpack(
                    assetManager.open(internalRuntime.path+"/universal.tar.xz"),
                    assetManager.open(internalRuntime.path+"/bin-" + archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz"),
                    internalRuntime.name, version);
            MultiRTUtils.postPrepare(internalRuntime.name);
            return true;
        }catch (IOException e) {
            Log.e("NewJREAuto", "Internal JRE unpack failed", e);
            return false;
        }
    }

    private static InternalRuntime getInternalRuntime(Runtime runtime) {
        for(InternalRuntime internalRuntime : InternalRuntime.values()) {
            if(internalRuntime.name.equals(runtime.name)) return internalRuntime;
        }
        return null;
    }

    private static MathUtils.RankedValue<Runtime> getNearestInstalledRuntime(int targetVersion) {
        List<Runtime> runtimes = MultiRTUtils.getRuntimes();
        return MathUtils.findNearestPositive(targetVersion, runtimes, (runtime)->runtime.javaVersion);
    }

    private static MathUtils.RankedValue<InternalRuntime> getNearestInternalRuntime(int targetVersion) {
        List<InternalRuntime> runtimeList = Arrays.asList(InternalRuntime.values());
        return MathUtils.findNearestPositive(targetVersion, runtimeList, (runtime)->runtime.majorVersion);
    }


    private static void selectRuntime(MinecraftProfile profile, String runtimeName) {
        profile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + runtimeName;
        LauncherProfiles.write();
    }

    private static int getDownloadableJavaMajor(int requiredVersion, int architecture) {
        for (int majorVersion : DOWNLOADABLE_JAVA_MAJORS) {
            if (majorVersion < requiredVersion) continue;
            if (architecture == Architecture.ARCH_X86 && majorVersion > 17) continue;
            return majorVersion;
        }
        return -1;
    }

    private static boolean downloadAndInstallRuntime(Activity activity, MinecraftProfile profile,
                                                      int requiredVersion) {
        int architecture = Tools.DEVICE_ARCHITECTURE;
        String architectureName = archAsString(architecture);
        int downloadMajor = getDownloadableJavaMajor(requiredVersion, architecture);
        if ("UNSUPPORTED_ARCH".equals(architectureName) || downloadMajor < 0) {
            showRuntimeFail(activity, requiredVersion);
            return false;
        }

        String runtimeName = "External-" + downloadMajor;
        Runtime existingRuntime = MultiRTUtils.read(runtimeName);
        if (existingRuntime.javaVersion >= requiredVersion) {
            selectRuntime(profile, runtimeName);
            return true;
        }

        String assetName = "jre" + downloadMajor + "-android-" + architectureName + ".tar.xz";
        String url = RUNTIME_RELEASE_BASE + "download_jre" + downloadMajor + "/" + assetName;
        File archive = new File(Tools.DIR_CACHE, "omni-" + assetName);
        DownloaderProgressWrapper progress = new DownloaderProgressWrapper(
                R.string.newdl_downloading_jre_runtime, ProgressLayout.DOWNLOAD_MINECRAFT);
        progress.extraString = String.valueOf(downloadMajor);

        try {
            DownloadUtils.downloadFileMonitored(url, archive, null, progress);
            MultiRTUtils.installRuntimeNamed(Tools.NATIVE_LIB_DIR,
                    new FileInputStream(archive), runtimeName);
            MultiRTUtils.postPrepare(runtimeName);
            Runtime installedRuntime = MultiRTUtils.forceReread(runtimeName);
            if (installedRuntime.versionString == null ||
                    installedRuntime.javaVersion < requiredVersion) {
                throw new IOException("Downloaded Java runtime is missing or too old");
            }
            selectRuntime(profile, runtimeName);
            return true;
        } catch (Exception e) {
            Log.e("NewJREAuto", "Failed to download or install Java " + downloadMajor, e);
            return false;
        } finally {
            if (archive.exists() && !archive.delete()) {
                Log.w("NewJREAuto", "Could not remove temporary runtime archive " + archive);
            }
            ProgressKeeper.submitProgress(ProgressLayout.DOWNLOAD_MINECRAFT, -1, -1);
        }
    }

    /** @return true if everything is good, false otherwise. */
    public static boolean installNewJreIfNeeded(Activity activity, JMinecraftVersionList.Version versionInfo) {
        if (versionInfo == null) return true;
        int gameRequiredVersion = versionInfo.javaVersion == null ||
                versionInfo.javaVersion.majorVersion <= 0
                ? 8 : versionInfo.javaVersion.majorVersion;
        if (versionInfo.javaVersion != null &&
                "jre-legacy".equalsIgnoreCase(versionInfo.javaVersion.component)) {
            gameRequiredVersion = 8;
        }

        LauncherProfiles.load();
        AssetManager assetManager = activity.getAssets();
        MinecraftProfile minecraftProfile = LauncherProfiles.getCurrentProfile();
        String profileRuntime = Tools.getSelectedRuntime(minecraftProfile);
        Runtime runtime = MultiRTUtils.read(profileRuntime);
        if (runtime.javaVersion >= gameRequiredVersion) {
            InternalRuntime internalRuntime = getInternalRuntime(runtime);
            if (internalRuntime == null || checkInternalRuntime(assetManager, internalRuntime)) {
                return true;
            }
        }

        MathUtils.RankedValue<?> nearestInstalledRuntime = getNearestInstalledRuntime(gameRequiredVersion);
        if (nearestInstalledRuntime != null) {
            Runtime installedRuntime = (Runtime) nearestInstalledRuntime.value;
            InternalRuntime internalRuntime = getInternalRuntime(installedRuntime);
            if (internalRuntime == null || checkInternalRuntime(assetManager, internalRuntime)) {
                selectRuntime(minecraftProfile, installedRuntime.name);
                return true;
            }
        }

        MathUtils.RankedValue<InternalRuntime> nearestInternalRuntime =
                getNearestInternalRuntime(gameRequiredVersion);
        if (nearestInternalRuntime != null &&
                checkInternalRuntime(assetManager, nearestInternalRuntime.value)) {
            selectRuntime(minecraftProfile, nearestInternalRuntime.value.name);
            return true;
        }

        // Download only the runtime needed for this device's ABI instead of bundling every version
        // and architecture in the APK.
        return downloadAndInstallRuntime(activity, minecraftProfile, gameRequiredVersion);
    }

    private static void showRuntimeFail(Activity activity, int requiredVersion) {
        Tools.dialogOnUiThread(activity, activity.getString(R.string.global_error),
                activity.getString(R.string.multirt_nocompatiblert, requiredVersion));
    }

    private enum InternalRuntime {
        JRE_17(17, "Internal-17", "components/jre-new"),
        JRE_21(21, "Internal-21", "components/jre-21");
        public final int majorVersion;
        public final String name;
        public final String path;
        InternalRuntime(int majorVersion, String name, String path) {
            this.majorVersion = majorVersion;
            this.name = name;
            this.path = path;
        }
    }

}