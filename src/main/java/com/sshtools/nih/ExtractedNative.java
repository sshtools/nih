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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ExtractedNative {

	private static final Logger LOG = System.getLogger(ExtractedNative.class.getName());

//	static final String[] LOCATIOxNS = { "darwin/libpty.dylib", "freebsd/x86/libpty.so", "freebsd/x86-64/libpty.so",
//			"linux/x86/libpty.so", "linux/x86-64/libpty.so", "linux/aarch64/libpty.so", "linux/arm/libpty.so",
//			"linux/ppc64le/libpty.so", "linux/mips64el/libpty.so", "win/aarch64/conpty.dll",
//			"win/aarch64/OpenConsole.exe", "win/aarch64/win-helper.dll", "win/aarch64/winpty-agent.exe",
//			"win/aarch64/winpty.dll", "win/x86/winpty-agent.exe", "win/x86/winpty.dll", "win/x86-64/conpty.dll",
//			"win/x86-64/OpenConsole.exe", "win/x86-64/cyglaunch.exe", "win/x86-64/win-helper.dll",
//			"win/x86-64/winpty-agent.exe", "win/x86-64/winpty.dll" };
	// static final String DEFAULT_RESOURCE_NAME_PREFIX =
	// "resources/com/pty4j/native/";
	static final String DEFAULT_RESOURCE_NAME_PREFIX = "";

	private static final ExtractedNative INSTANCE = new ExtractedNative();
	private String myResourceOsArchSubPath;
	private String myResourceNamePrefix;
	private boolean myInitialized;
	private volatile File myDestDir;

	private ExtractedNative() {
		this(null, null);
	}

	ExtractedNative(String resourceOsArchSubPath, String resourceNamePrefix) {
		myResourceOsArchSubPath = resourceOsArchSubPath;
		myResourceNamePrefix = resourceNamePrefix;
	}

	public static ExtractedNative getInstance() {
		return INSTANCE;
	}

	File getDestDir() {
		if (!myInitialized) {
			init();
		}
		return myDestDir;
	}

	private void init() {
		try {
			myResourceOsArchSubPath = Objects.requireNonNullElse(myResourceOsArchSubPath,
					PtyUtil.getNativeLibraryOsArchSubPath());
			myResourceNamePrefix = Objects.requireNonNullElse(myResourceNamePrefix, DEFAULT_RESOURCE_NAME_PREFIX);
			synchronized (this) {
				if (!myInitialized) {
					doInit();
				}
				myInitialized = true;
			}
		} catch (Exception e) {
			throw new IllegalStateException("Cannot extract pty4j native " + myResourceOsArchSubPath, e);
		}
	}

	private void doInit() throws IOException {
		long startTimeNano = System.nanoTime();
		Path destDir = getOrCreateDestDir();
		if (LOG.isLoggable(Level.DEBUG)) {
			LOG.log(Level.DEBUG, "Found {0} in {1}", destDir, pastTime(startTimeNano));
		}
		List<Path> children;
		try (Stream<Path> stream = Files.list(destDir)) {
			children = stream.collect(Collectors.<Path>toList());
		}
		if (LOG.isLoggable(Level.DEBUG)) {
			LOG.log(Level.DEBUG, "Listed files in {0}", pastTime(startTimeNano));
		}
		Map<String, Path> resourceToFileMap = new HashMap<>();
		for (Path child : children) {
			String resourceName = getResourceName(child.getFileName().toString());
			resourceToFileMap.put(resourceName, child);
		}
		Set<String> bundledResourceNames = getBundledResourceNames();
		boolean upToDate = isUpToDate(bundledResourceNames, resourceToFileMap);
		if (LOG.isLoggable(Level.DEBUG)) {
			LOG.log(Level.DEBUG, "Checked upToDate in {0}", pastTime(startTimeNano));
		}
		if (!upToDate) {
			for (Path child : children) {
				Files.delete(child);
			}
			if (LOG.isLoggable(Level.DEBUG)) {
				LOG.log(Level.DEBUG, "Cleared directory in {0}", pastTime(startTimeNano));
			}
			for (String bundledResourceName : bundledResourceNames) {
				copy(bundledResourceName, destDir);
			}
			if (LOG.isLoggable(Level.DEBUG)) {
				LOG.log(Level.DEBUG, "Copied {0} in {1}", bundledResourceNames, pastTime(startTimeNano));
			}
		}
		myDestDir = destDir.toFile();
		LOG.log(Level.INFO, "Extracted pty4j native in {0}", pastTime(startTimeNano));
	}

	private Path getOrCreateDestDir() throws IOException {
		String staticParentDirPath = System.getProperty("nih.tmpdir");
		String prefix = "nih-" + myResourceOsArchSubPath.replace('/', '-');
		if (staticParentDirPath != null && !staticParentDirPath.trim().isEmpty()) {
			// It's assumed that "nih.tmpdir" directory should not be used by several
			// processes with nih simultaneously.
			// And several nih.jar versions can't coexist in classpath of a process.
			Path staticParentDir = Paths.get(staticParentDirPath);
			if (staticParentDir.isAbsolute()) {
				Path staticDir = staticParentDir.resolve(prefix);
				if (Files.isDirectory(staticDir)) {
					return staticDir;
				}
				if (Files.isDirectory(staticParentDir)) {
					if (Files.exists(staticDir)) {
						Files.delete(staticDir);
					}
					return Files.createDirectory(staticDir);
				}
			}
		}
		Path tempDirectory = Files.createTempDirectory(prefix + "-");
		tempDirectory.toFile().deleteOnExit();
		return tempDirectory;
	}

	private boolean isUpToDate(Set<String> bundledResourceNames, Map<String, Path> resourceToFileMap) {
		if (!bundledResourceNames.equals(resourceToFileMap.keySet())) {
			return false;
		}
		for (Map.Entry<String, Path> entry : resourceToFileMap.entrySet()) {
			try {
				URL bundledUrl = getBundledResourceUrl(entry.getKey());
				byte[] bundledContentChecksum = md5(bundledUrl.openStream());
				byte[] fileContentChecksum = md5(Files.newInputStream(entry.getValue()));
				if (!Arrays.equals(bundledContentChecksum, fileContentChecksum)) {
					return false;
				}
			} catch (Exception e) {
				LOG.log(Level.ERROR, "Cannot compare md5 checksums", e);
				return false;
			}
		}
		return true;
	}

	private static byte[] md5(InputStream in) throws IOException, NoSuchAlgorithmException {
		try {
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			byte[] buffer = new byte[8192];
			int bufferSize;
			while ((bufferSize = in.read(buffer)) >= 0) {
				md5.update(buffer, 0, bufferSize);
			}
			return md5.digest();
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				LOG.log(Level.ERROR, "Cannot close", e);
			}
		}
	}

	private Set<String> getBundledResourceNames() {
		// noinspection Convert2Diamond
		Set<String> resourceNames = new HashSet<String>();
		String prefix = myResourceOsArchSubPath + "/";
		for (String location : getBundledResourceLocations()) {
			if (location.startsWith(prefix)) {
				resourceNames.add(myResourceNamePrefix + location);
			}
		}
		return resourceNames;
	}
	
	private static Set<String> getBundledResourceLocations() {
		var res = ExtractedNative.class.getClassLoader().getResourceAsStream("META-INF/nih.bundle");
		if(res == null) {
			throw new IllegalStateException("No META-INF/nih.bundle resource found");
		}
		var s = new LinkedHashSet<String>();
		try(var in = new BufferedReader(new InputStreamReader(res))) {
			String l;
			while( ( l = in.readLine() ) != null) {
				l = l.trim();
				if(!l.startsWith("#") && !l.equals("")) {
					s.add(l);
				}
			}
		}
		catch(IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
		return s;
	}

	private String getResourceName(String fileName) {
		return (myResourceNamePrefix == null ? "" : myResourceNamePrefix) + myResourceOsArchSubPath + "/" + fileName;
	}

	private void copy(String resourceName, Path destDir) throws IOException {
		URL url = getBundledResourceUrl(resourceName);
		int lastNameInd = resourceName.lastIndexOf('/');
		String name = lastNameInd != -1 ? resourceName.substring(lastNameInd + 1) : resourceName;
		InputStream inputStream = url.openStream();
		// noinspection TryFinallyCanBeTryWithResources
		try {
			Files.copy(inputStream, destDir.resolve(name));
		} finally {
			inputStream.close();
		}
	}

	private URL getBundledResourceUrl(String resourceName) throws IOException {
		ClassLoader classLoader = ExtractedNative.class.getClassLoader();
		URL url = classLoader.getResource(resourceName);
		if (url == null) {
			ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
			if (contextClassLoader != null) {
				url = contextClassLoader.getResource(resourceName);
			}
			if (url == null) {
				throw new IOException("Unable to load " + resourceName);
			}
		}
		return url;
	}

	private static String pastTime(long startTimeNano) {
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNano) + " ms";
	}
}
