/*
 * Copyright © 2023 JAdaptive Limited (support@jadaptive.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
