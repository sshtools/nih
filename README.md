# nih

![Maven Build/Test JDK 22](https://github.com/sshtools/nih/actions/workflows/maven.yml/badge.svg)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.sshtools/nih/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.sshtools/nih)
[![Coverage Status](https://coveralls.io/repos/github/sshtools/nih/badge.svg)](https://coveralls.io/github/sshtools/nih)
[![javadoc](https://javadoc.io/badge2/com.sshtools/nih/javadoc.svg)](https://javadoc.io/doc/com.sshtools/nih)
![JPMS](https://img.shields.io/badge/JPMS-com.sshtools.nih-purple) 

NIH (or **Native Integration Helper**), is a tiny library for Java 22 and above that helps with loading native libraries. Based on code from JNA, it deals with bundling platform specific libraries along with your application jars, and overcoming some problems with locating such libraries when using the new FFMAPI available in modern Java.

## Configuring your project

The library is available in Maven Central, so configure your project according to the
build system you use. For example, for Maven itself :-

```xml
    <dependencies>
        <dependency>
            <groupId>com.sshtools</groupId>
            <artifactId>nih</artifactId>
            <version>1.0.1</version>
        </dependency>
    </dependencies>
```

### Snapshots

*Development builds may be available in the snapshots repository*

```xml

<repositories>
    <repository>
        <id>oss-snapshots</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
        <releases>
            <enabled>false</enabled>
        </releases>
    </repository>
</repositories>
    
..

<dependencies>
    <dependency>
        <groupId>com.sshtools</groupId>
        <artifactId>nih</artifactId>
        <version>1.0.1-SNAPSHOT</version>
    </dependency>
</dependencies>
```

## How To Use With OS Supplied Libraries

Simply use `Native.load()` methods instead of FFMAPIs default methods to obtain a `SymbolLookup`. This deals with several platform specific problems that as of writing, the base Java tools do not deal with (Linux library paths with symbolic links, and Mac OS frameworks do not seem to be handled well). 

For example, `Native.load("c", Arena.ofAuto());` will locate that standard C library. 

## How To Use With Bundled Libraries

First, include compiled shared libraries as a Java resource (e.g. `src/main/resources`)  in the sub-path `META-INF/shared-libraries/<os>/<arch>`. 

Then to load the library in Java and obtain a `SymbolLookup`, use `Native.load()` methods.

For example, say you are working on 64-bit Linux and had a native shared library compiled from `tray.c`. Your native `Makefile` produces a `libtray.so`.

You would place `libtray.so` into `src/main/resource/META-INF/shared-libraries/linux/x86-64`, and then use `Native.load("tray", Arena.ofAuto())` to obtain the library handle.

If you then want to add Windows support, you would  then place your `tray.dll` into `src/main/resource/META-INF/shared-libraries/windows/x86-64`.

## Origin

The 2 classes in this library originated from JNA, but were adapted to be useful with Java's own FFMAPI. This code, and our minor additions to it are licensed under LGPL 2.1 or later and Apache License 2.0. (starting with JNA version 4.0.0).
