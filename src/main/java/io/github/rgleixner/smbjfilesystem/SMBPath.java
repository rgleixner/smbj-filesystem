package io.github.rgleixner.smbjfilesystem;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import io.github.rgleixner.smbjfilesystem.SMBFileSystem.DiskShareAction;
import io.github.rgleixner.smbjfilesystem.SMBFileSystem.DiskShareAction2;

public final class SMBPath implements Path {

	private final SMBFileSystem fileSystem;

	private final String[] components;

	private final boolean absolute;

	private final boolean folder;

	static SMBPath fromPath(Path path) {
		if (!(path instanceof SMBPath)) {
			throw new IllegalArgumentException("The provided path '" + path.toString() + "' is not an SMB path.");
		}
		return (SMBPath) path;
	}

	SMBPath(SMBFileSystem fileSystem, URI uri) {
		if (!uri.getScheme().equals(SMBFileSystem.SMB_SCHEME)) {
			throw new IllegalArgumentException("The provided URI does not point to an SMB resource.");
		}

		this.fileSystem = fileSystem;
		this.components = SMBPathUtil.splitPath(uri.getPath());
		this.absolute = SMBPathUtil.isAbsolutePath(uri.getPath());
		this.folder = SMBPathUtil.isFolder(uri.getPath());
	}

	SMBPath(SMBFileSystem fileSystem, String path) {
		this.fileSystem = fileSystem;
		this.components = SMBPathUtil.splitPath(path);
		this.absolute = SMBPathUtil.isAbsolutePath(path);
		this.folder = SMBPathUtil.isFolder(path);
	}

	SMBPath(SMBFileSystem fileSystem, String first, String... more) {
		String[] result = Stream.concat(Arrays.stream(new String[] { first }), Arrays.stream(more))
				.toArray(String[]::new);
		String path = SMBPathUtil.mergePath(result, 0, result.length, false, result[result.length - 1].endsWith("/"));

		this.fileSystem = fileSystem;
		this.components = SMBPathUtil.splitPath(path);
		this.absolute = SMBPathUtil.isAbsolutePath(path);
		this.folder = SMBPathUtil.isFolder(path);
	}

	@Override
	public SMBFileSystem getFileSystem() {
		return this.fileSystem;
	}

	@Override
	public boolean isAbsolute() {
		return this.absolute;
	}

	@Override
	public Path getRoot() {
		if (!this.absolute) {
			return null;
		}
		return new SMBPath(this.fileSystem, "/");
	}

	@Override
	public Path getFileName() {
		return new SMBPath(this.fileSystem, this.components[this.components.length - 1]);
	}

	@Override
	public Path getParent() {
		if (this.components.length <= 1) {
			return null;
		}
		String reduced = SMBPathUtil.mergePath(this.components, 0, this.components.length - 1, this.absolute, true);
		return new SMBPath(this.fileSystem, reduced);
	}

	@Override
	public int getNameCount() {
		return this.components.length;
	}

	@Override
	public Path getName(int index) {
		if (index < 0 || index > this.components.length) {
			throw new IllegalArgumentException("The provided index is out of bounds.");
		}
		String reduced = SMBPathUtil.mergePath(this.components, index, index, false,
				index == this.components.length - 1 && this.folder);
		return new SMBPath(this.fileSystem, reduced);
	}

	@Override
	public Path subpath(int beginIndex, int endIndex) {
		if (beginIndex < 0 || endIndex > this.components.length) {
			throw new IllegalArgumentException("Index out of bounds.");
		}
		if (beginIndex > endIndex) {
			throw new IllegalArgumentException("beginIndex must be smaller than endIndex.");
		}
		String reduced = SMBPathUtil.mergePath(this.components, beginIndex, endIndex, false,
				endIndex == this.components.length - 1 && this.folder);
		return new SMBPath(this.fileSystem, reduced);
	}

	@Override
	public boolean startsWith(Path other) {
		String path = SMBPathUtil.mergePath(this.components, 0, this.components.length, this.absolute, this.folder);
		return path.startsWith(other.toString());
	}

	@Override
	public boolean endsWith(Path other) {
		String path = SMBPathUtil.mergePath(this.components, 0, this.components.length, this.absolute, this.folder);
		return path.endsWith(other.toString());
	}

	@Override
	public Path normalize() {
		ArrayList<String> normalized = new ArrayList<>();
		for (final String component : this.components) {
			if (component.equals(".")) {
				continue;
			} else if (component.equals("..") && normalized.size() > 1) {
				normalized.remove(normalized.size() - 1);
			} else if (component.equals("..") && normalized.size() > 0) {
				continue;
			} else {
				normalized.add(component);
			}
		}
		String path = SMBPathUtil.mergePath(normalized.toArray(new String[0]), 0, normalized.size(), this.absolute,
				this.folder);
		return new SMBPath(this.fileSystem, path);
	}

	@Override
	public Path resolve(Path other) {
		assertPath(other);

		if (other.isAbsolute()) {
			return other;
		}

		String[] result = new String[other.getNameCount() + this.getNameCount()];
		System.arraycopy(this.components, 0, result, 0, this.getNameCount());
		System.arraycopy(((SMBPath) other).components, 0, result, this.getNameCount(), other.getNameCount());
		String path = SMBPathUtil.mergePath(result, 0, result.length, this.absolute, ((SMBPath) other).folder);
		return new SMBPath(this.fileSystem, path);
	}

	@Override
	public Path relativize(Path other) {
		SMBPath target = assertPath(other);

		boolean common = true;
		int lastIndex = 0;
		final List<String> newPath = new ArrayList<>();
		for (int i = 0; i < this.components.length; i++) {
			if (common) {
				if (i < target.components.length) {
					if (this.components[i].equals(target.components[i])) {
						lastIndex++;
					} else {
						common = false;
						newPath.add("..");
					}
				} else {
					newPath.add("..");
					common = false;
				}
			} else {
				newPath.add("..");
			}
		}

		if (lastIndex < target.components.length) {
			newPath.addAll(Arrays.asList(target.components).subList(lastIndex, target.components.length));
		}

		String[] array = new String[newPath.size()];
		String path = SMBPathUtil.mergePath(newPath.toArray(array), 0, newPath.size(), false, target.folder);
		return new SMBPath(this.fileSystem, path);
	}

	@Override
	public URI toUri() {
		String path = SMBPathUtil.mergePath(this.components, 0, this.components.length, this.absolute, this.folder);
		return URI.create(this.fileSystem.getFQN() + SMBPathUtil.encodePath(path, StandardCharsets.UTF_8));
	}

	@Override
	public Path toAbsolutePath() {
		if (this.isAbsolute()) {
			return this;
		}
		return new SMBPath(this.fileSystem, "/").resolve(this);
	}

	@Override
	public int compareTo(Path other) {
		assertPath(other);

		String path = SMBPathUtil.mergePath(this.components, 0, this.components.length, this.absolute, this.folder);
		String otherPath = SMBPathUtil.mergePath(((SMBPath) other).components, 0, ((SMBPath) other).components.length,
				((SMBPath) other).absolute, ((SMBPath) other).folder);
		return path.compareTo(otherPath);
	}

	@Override
	public Path toRealPath(LinkOption... options) {
		return toAbsolutePath();
	}

	@Override
	public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) {
		throw new UnsupportedOperationException("FileWatchers are currently not supported.");
	}

	@Override
	public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) {
		throw new UnsupportedOperationException("FileWatchers are currently not supported.");
	}

	@Override
	public String toString() {
		return SMBPathUtil.mergePath(this.components, 0, this.components.length, this.absolute, this.folder);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(components);
		result = prime * result + Objects.hash(fileSystem, absolute, folder);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof SMBPath)) {
			return false;
		}
		SMBPath other = (SMBPath) obj;
		return Objects.equals(fileSystem, other.fileSystem) && Arrays.equals(components, other.components)
				&& absolute == other.absolute && folder == other.folder;
	}

	private SMBPath assertPath(Path other) {
		if (!(other instanceof SMBPath)) {
			throw new IllegalArgumentException("Path must be an instanceof of SMBPath.");
		}
		if (((SMBPath) other).fileSystem != this.fileSystem) {
			throw new IllegalArgumentException("Path must be on the same filesystem.");
		}
		return (SMBPath) other;
	}

	<T> T call(DiskShareAction<T> action) throws IOException {
		return getFileSystem().call(this, action);
	}

	<T> T call(SMBPath other, DiskShareAction2<T> action) throws IOException {
		return getFileSystem().call(this, other, action);
	}

}
