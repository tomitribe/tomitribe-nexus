# tomitribe-nexus

A read-only [`java.nio.file.FileSystem`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/FileSystem.html)
over a remote Maven repository's HTML index — Nexus 2, Nexus 3, Maven Central, or any
`mod_autoindex`-style listing.

You point it at a repository's base URL and get back an ordinary `Path`. From there you use the
standard `java.nio.file.Files` API — `walk`, `list`, `copy`, `readAttributes` — to browse and
download artifacts as if the remote repository were a local directory tree. No new vocabulary to
learn: if you know NIO, you know this.

```java
Path root = Nexus.builder()
        .baseUri("https://repo1.maven.org/maven2/")
        .build();

Path version = root.resolve("org/apache/tomee/apache-tomee/9.0.1");

try (Stream<Path> tree = Files.walk(version)) {
    tree.filter(Files::isRegularFile)
        .filter(p -> p.getFileName().toString().endsWith(".tar.gz"))
        .forEach(p -> copy(p, target.resolve(p.getFileName().toString())));
}
```

## Dependency

```xml
<dependency>
  <groupId>org.tomitribe.nexus</groupId>
  <artifactId>tomitribe-nexus</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Requires Java 17+.

## Getting a root

`Nexus.builder()` is the only entry point. `build()` returns the `Path` for the repository root.

```java
// Anonymous (e.g. Maven Central)
Path root = Nexus.builder()
        .baseUri("https://repo1.maven.org/maven2/")
        .build();

// Authenticated (HTTP Basic)
Path root = Nexus.builder()
        .baseUri("https://nexus.example/content/repositories/releases/")
        .credentials("user", "pass")   // optional — omit for anonymous
        .build();
```

Credentials, when supplied, are sent only to the configured base host — they are never attached to
a request for any other host.

## Browsing

The root is a `Path` rooted at the base URL. Build sub-paths with `resolve(...)`, exactly as you
would on the default filesystem:

```java
Path artifact = root.resolve("org/apache/tomee/apache-tomee");

// relative paths built with the ordinary NIO API work too — including on Windows,
// where the separator differs
Path same = root.resolve(Path.of("org", "apache", "tomee", "apache-tomee"));
```

**List the immediate children** of a directory (e.g. the available versions):

```java
try (Stream<Path> children = Files.list(artifact)) {
    children.filter(Files::isDirectory)              // version folders; skip maven-metadata.xml etc.
            .map(p -> p.getFileName().toString())
            .sorted()
            .forEach(System.out::println);
}

// or a DirectoryStream
try (DirectoryStream<Path> children = Files.newDirectoryStream(artifact)) {
    for (Path child : children) System.out.println(child);
}
```

**Walk a whole subtree:**

```java
try (Stream<Path> tree = Files.walk(root.resolve("org/apache/tomee/apache-tomee/9.0.1"))) {
    tree.forEach(System.out::println);
}
```

## Downloading

Copy a remote file straight to local disk — it's just `Files.copy` across providers:

```java
Path remote = root.resolve("org/apache/tomee/apache-tomee/9.0.1/apache-tomee-9.0.1.tar.gz");
Files.copy(remote, Path.of("/tmp/apache-tomee-9.0.1.tar.gz"), StandardCopyOption.REPLACE_EXISTING);
```

Or stream the bytes yourself:

```java
try (InputStream in = Files.newInputStream(remote)) {
    // ...
}
```

## Attributes

Size and last-modified come back through the standard attribute API. When they were present in the
directory listing they cost nothing; otherwise they are filled by a single `HEAD` on demand:

```java
BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
attrs.size();
attrs.lastModifiedTime();
attrs.isDirectory();
attrs.isRegularFile();

// or the convenience methods
long size = Files.size(p);
FileTime modified = Files.getLastModifiedTime(p);
boolean exists = Files.exists(p);          // false for a 404, never throws
boolean dir = Files.isDirectory(p);
```

## How it behaves

- **Read-only.** Every mutating operation (`createDirectory`, `delete`, `move`, writing) throws
  `ReadOnlyFileSystemException`. Reading bytes from a directory, or listing a file, throws
  `UnsupportedOperationException`. A path that doesn't exist surfaces as `NoSuchFileException`.

- **Rooted at the base — off-base is unrepresentable.** The root `/` *is* the base URL, and `..`
  can never climb above it. There is no host or scheme to point elsewhere, so you cannot, even by
  accident, aim an authenticated client at another location.

- **Lazy.** Naming a path (`root.resolve(...)`) costs nothing — no network. The first operation
  that needs the remote (`Files.isDirectory`, `newInputStream`, `readAttributes`, ...) resolves it
  with a single `HEAD`, cached thereafter. A `Files.walk` is request-minimal: one listing per
  directory, with each entry's kind, size, and date read from that listing — it never `HEAD`s a
  file just to walk past it.

- **Server-aware.** Directory-vs-file is determined from the listing and, for a hand-built path,
  from the `HEAD` response — handling Nexus 2 (a directory `HEAD` is `200` with no `Content-Type`),
  Nexus 3 / autoindex (`text/html` listings), and Maven Central.

## Limitations

It is a genuine `java.nio.file.Path`, but a *remote, read-only* one, so two NIO operations don't
apply:

- **`Path.toFile()` throws.** A Nexus path is not backed by a local `java.io.File`. Use `Files.copy`
  to bring it to disk, then work with the local file.
- **Byte-channel reads aren't implemented.** `Files.newByteChannel` (and therefore
  `Files.readAllBytes` / `Files.readString`) is unsupported — read with `Files.newInputStream` or
  copy with `Files.copy` instead.

## License

Apache License 2.0.
