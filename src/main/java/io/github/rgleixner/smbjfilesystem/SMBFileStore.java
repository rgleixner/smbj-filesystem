package io.github.rgleixner.smbjfilesystem;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.util.Objects;

public final class SMBFileStore extends FileStore {

	static final String FILE_STORE_TYPE = "basic";

	private final SMBPath path;

	SMBFileStore(SMBPath path) {
		this.path = path;
	}

	@Override
	public String name() {
		return this.path.getFileSystem().getFQN().toString();
	}

	@Override
	public String type() {
		return FILE_STORE_TYPE;
	}

	@Override
	public boolean isReadOnly() {
		return false;
	}

	@Override
	public long getTotalSpace() throws IOException {
		return path.call((share, __) -> share.getShareInformation().getTotalSpace());
	}

	@Override
	public long getUsableSpace() throws IOException {
		return path.call((share, __) -> share.getShareInformation().getCallerFreeSpace());
	}

	@Override
	public long getUnallocatedSpace() throws IOException {
		return path.call((share, __) -> share.getShareInformation().getFreeSpace());
	}

	@Override
	public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
		return type.isAssignableFrom(SMBFileAttributeView.class);
	}

	@Override
	public boolean supportsFileAttributeView(String name) {
		return name.equals(SMBFileAttributeView.FILE_ATTRIBUTE_VIEW_NAME);
	}

	@Override
	public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
		return null;
	}

	@Override
	public Object getAttribute(String attribute) {
		throw new UnsupportedOperationException(
				"File store attribute views are not supported for the current implementation of SMBFileStore.");
	}

	@Override
	public int hashCode() {
		return Objects.hash(path);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		SMBFileStore other = (SMBFileStore) obj;
		return Objects.equals(path, other.path);
	}

	SMBPath getPath() {
		return path;
	}

}
