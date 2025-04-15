package io.github.rgleixner.smbjfilesystem;

import java.io.IOException;
import java.net.URI;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.share.DiskShare;

import io.github.rgleixner.smbjfilesystem.SMBClientWrapper.SMBShareWrapper;

public final class SMBFileSystem extends FileSystem {

	@FunctionalInterface
	interface DiskShareAction<T> {

		T run(DiskShare share, String relativePath) throws IOException;

	}

	@FunctionalInterface
	interface DiskShareAction2<T> {

		T run(DiskShare share, String relativePath, DiskShare shareOther, String relativePathOther) throws IOException;

	}

	static final Logger LOGGER = LoggerFactory.getLogger(SMBFileSystem.class);

	static final String SMB_SCHEME = "smb";

	static final String PATH_SEPARATOR = "/";

	static final String SCHEME_SEPARATOR = "://";

	static final String CREDENTIALS_SEPARATOR = "@";

	private static final Set<String> SUPPORTED_FILE_ATTRIBUTE_VIEWS = Set
			.of(SMBFileAttributeView.FILE_ATTRIBUTE_VIEW_NAME);

	private final SMBFileSystemProvider provider;

	private final URI fqn;

	private final List<SMBFileStore> fileStores;

	private final SMBClientWrapper clientWrapper;

	static URI createFQN(URI uri) {
		if (!uri.getScheme().equals(SMBFileSystem.SMB_SCHEME)) {
			throw new IllegalArgumentException("The provided URI is not an SMB URI.");
		}
		if (uri.getAuthority() == null) {
			throw new IllegalArgumentException("The provided URI has no authority.");
		}
		if (uri.getPath() == null) {
			throw new IllegalArgumentException("The provided URI has no path.");
		}
		String authority = uri.getAuthority();
		String shareName = uri.getPath().substring(1, uri.getPath().indexOf('/', 1));
		return URI.create(
				SMB_SCHEME + SMBFileSystem.SCHEME_SEPARATOR + authority + SMBFileSystem.PATH_SEPARATOR + shareName);
	}

	SMBFileSystem(SMBFileSystemProvider provider, URI fqn, SMBClientWrapper clientWrapper) {
		this.provider = provider;
		this.fqn = fqn;
		this.clientWrapper = clientWrapper;
		this.fileStores = List.of(new SMBFileStore(new SMBPath(this, getSeparator())));
	}

	@Override
	public FileSystemProvider provider() {
		return this.provider;
	}

	@Override
	public void close() {
		if (isOpen()) {
			try {
				clientWrapper.close();
			} catch (Exception e) {
				SMBFileSystem.LOGGER.error("failed to close SMB filesystem", e);
			}
			this.provider.fileSystemCache.remove(this.fqn);
		}
	}

	@Override
	public boolean isOpen() {
		return this.provider.fileSystemCache.containsKey(this.fqn);
	}

	@Override
	public boolean isReadOnly() {
		if (!this.isOpen()) {
			throw new ClosedFileSystemException();
		}
		return false;
	}

	@Override
	public String getSeparator() {
		return SMBFileSystem.PATH_SEPARATOR;
	}

	@Override
	public Iterable<Path> getRootDirectories() {
		if (!this.isOpen()) {
			throw new ClosedFileSystemException();
		}
		return fileStores.stream().map(SMBFileStore::getPath).map(path -> (Path) path).toList();
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		if (!this.isOpen()) {
			throw new ClosedFileSystemException();
		}
		return fileStores.stream().map(fileStore -> (FileStore) fileStore).toList();
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		if (!this.isOpen()) {
			throw new ClosedFileSystemException();
		}
		return SUPPORTED_FILE_ATTRIBUTE_VIEWS;
	}

	@Override
	public Path getPath(String first, String... more) {
		if (!this.isOpen()) {
			throw new ClosedFileSystemException();
		}
		return new SMBPath(this, first, more);
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		return new SMBPathMatcher(syntaxAndPattern);
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		throw new UnsupportedOperationException("The SMBFileSystem does not support UserPrincipalLookupServices.");
	}

	@Override
	public WatchService newWatchService() {
		throw new UnsupportedOperationException("The SMBFileSystem does not support WatchService.");
	}

	URI getFQN() {
		return fqn;
	}

	SMBShareWrapper getShare() throws IOException {
		return clientWrapper.getShare();
	}

	<T> T call(SMBPath path, DiskShareAction<T> action) throws IOException {
		if (!this.isOpen()) {
			throw new ClosedFileSystemException();
		}
		try (SMBShareWrapper share = getShare()) {
			String relativePath = path.toString();
			SMBFileSystem.LOGGER.debug("call share {} with relative path {}",
					share.getSmbShare().getSmbPath().toUncPath(), relativePath);
			return action.run(share.getSmbShare(), relativePath);
		} catch (SMBApiException e) {
			SMBFileSystem.LOGGER.trace(e.getMessage(), e);
			throw SMBExceptionUtil.translateToNIOException(e, path);
		}
	}

	<T> T call(SMBPath path, SMBPath pathOther, DiskShareAction2<T> action) throws IOException {
		if (!this.isOpen()) {
			throw new ClosedFileSystemException();
		}
		try (SMBShareWrapper share = getShare(); SMBShareWrapper otherShare = pathOther.getFileSystem().getShare()) {
			String relativePath = path.toString();
			String relativePathOther = pathOther.toString();
			SMBFileSystem.LOGGER.debug("call share {} with relative path {} on other share {} with relative path",
					share.getSmbShare().getSmbPath().toUncPath(), relativePath,
					otherShare.getSmbShare().getSmbPath().toUncPath(), relativePathOther);
			return action.run(share.getSmbShare(), relativePath, otherShare.getSmbShare(), relativePathOther);
		} catch (SMBApiException e) {
			SMBFileSystem.LOGGER.trace(e.getMessage(), e);
			throw SMBExceptionUtil.translateToNIOException(e, path, pathOther);
		}
	}

}
