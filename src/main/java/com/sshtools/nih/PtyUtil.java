package com.sshtools.nih;

import java.io.File;
import java.util.Map;

/**
 * @author traff
 */
public class PtyUtil {

	public static final String PREFERRED_NATIVE_FOLDER_KEY = "nih.preferred.native.folder";

	public static String[] toStringArray(Map<String, String> environment) {
		if (environment == null)
			return new String[0];
		return environment.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
				.toArray(String[]::new);
	}

	private static File getPreferredLibPtyFolder() {
		String path = System.getProperty(PREFERRED_NATIVE_FOLDER_KEY);
		File dir = path != null && !path.isEmpty() ? new File(path) : null;
		if (dir != null && dir.isDirectory()) {
			return dir.getAbsoluteFile();
		}
		return null;
	}

	public static File resolveNativeFile(String fileName) throws IllegalStateException {
		File preferredLibPtyFolder = getPreferredLibPtyFolder();
		if (preferredLibPtyFolder != null) {
			return resolveNativeFileFromFS(preferredLibPtyFolder, fileName);
		}
		File destDir = ExtractedNative.getInstance().getDestDir();
		return new File(destDir, fileName);
	}

	private static File resolveNativeFileFromFS(File libPtyFolder, String fileName) {
		String nativeLibraryResourcePath = getNativeLibraryOsArchSubPath();
		return new File(new File(libPtyFolder, nativeLibraryResourcePath), fileName);
	}

	static String getNativeLibraryOsArchSubPath() {
		int osType = Platform.getOSType();
		String arch = Platform.ARCH;
		if (osType == Platform.WINDOWS) {
			return "win/" + arch;
		}
		if (osType == Platform.MAC) {
			return "darwin";
		}
		if (osType == Platform.LINUX) {
			return "linux/" + arch;
		}
		if (osType == Platform.FREEBSD) {
			return "freebsd/" + arch;
		}
		throw new IllegalStateException("No native support for " + "OS name: " + System.getProperty("os.name")
				+ " (JVM OS type: " + Platform.getOSType() + ")" + ", arch: " + System.getProperty("os.arch")
				+ " (JVM arch: " + Platform.ARCH + ")");
	}
}
