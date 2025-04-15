package io.github.rgleixner.smbjfilesystem;

import static com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_NORMAL;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mserref.NtStatus;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileBasicInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.protocol.commons.buffer.Buffer.BufferException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.auth.GSSAuthenticationContext;
import com.hierynomus.smbj.share.Directory;
import com.hierynomus.smbj.share.DiskEntry;
import com.hierynomus.smbj.share.File;

import io.github.rgleixner.smbjfilesystem.SMBClientWrapper.SMBClientWrapperImpl;

public final class SMBFileSystemProvider extends FileSystemProvider {

	public static final String PROPERTY_FQN = "smbj-filesystem.fqn";

	public static final String PROPERTY_JAAS_SERVICE_NAME = "smbj-filesystem.provider.jaas-service-name";

	public static final String PROPERTY_DOMAIN = "smbj-filesystem.provider.domain";

	public static final String PROPERTY_USERNAME = "smbj-filesystem.provider.username";

	public static final String PROPERTY_PASSWORD = "smbj-filesystem.provider.password";

	private static Function<Map<String, ?>, SMBClient> clientFactory = (Map<String, ?> env) -> {
		return new SMBClient(SmbConfig.createDefaultConfig());
	};

	private static Function<Map<String, ?>, AuthenticationContext> authenticationContextFactory = (
			Map<String, ?> env) -> {
		URI fqn = (URI) env.get(PROPERTY_FQN);

		if (fqn.getUserInfo() == null) {
			String jaasServiceName = (String) env.get(PROPERTY_JAAS_SERVICE_NAME);
			if (jaasServiceName != null) {
				return getGSSAuthenticationContext(jaasServiceName);
			}
		}

		@SuppressWarnings("unchecked")
		String username = (String) ((Map<String, Object>) env).getOrDefault(PROPERTY_USERNAME, "");
		@SuppressWarnings("unchecked")
		String password = (String) ((Map<String, Object>) env).getOrDefault(PROPERTY_PASSWORD, "");
		String domain = (String) env.get(PROPERTY_DOMAIN);

		if (fqn.getUserInfo() != null) {
			String[] userInfo = fqn.getUserInfo().split(":");
			username = userInfo[0];
			if (userInfo.length > 1) {
				password = userInfo[1];
			}
		}

		return new AuthenticationContext(username, password.toCharArray(), domain);
	};

	private static Function<Map<String, ?>, SMBClientWrapper> clientWrapperFactory = (Map<String, ?> env) -> {
		return new SMBClientWrapperImpl((URI) env.get(PROPERTY_FQN), clientFactory.apply(env),
				authenticationContextFactory.apply(env));
	};

	private static GSSAuthenticationContext getGSSAuthenticationContext(String jaasServiceName) {
		try {
			LoginContext loginContext = new LoginContext(jaasServiceName);
			loginContext.login();
			Subject subject = loginContext.getSubject();

			KerberosPrincipal krbPrincipal = subject.getPrincipals(KerberosPrincipal.class).iterator().next();
			GSSCredential creds = Subject.doAs(subject, new PrivilegedExceptionAction<GSSCredential>() {

				@Override
				public GSSCredential run() throws GSSException {
					final GSSManager manager = GSSManager.getInstance();
					final GSSName name = manager.createName(krbPrincipal.getName(), GSSName.NT_USER_NAME);
					Oid[] mechs = manager.getMechsForName(name.getStringNameType());
					// OID mech = new Oid("1.2.840.113554.1.2.2") // KRB5
					// OID mech = new Oid("1.3.6.1.5.5.2"); // SPNEGO
					return manager.createCredential(name, GSSCredential.DEFAULT_LIFETIME, mechs[0],
							GSSCredential.INITIATE_ONLY);
				}
			});

			return new GSSAuthenticationContext(krbPrincipal.getName(), krbPrincipal.getRealm(), subject, creds);
		} catch (LoginException | PrivilegedActionException exception) {
			throw new RuntimeException(exception);
		}
	}

	final Map<URI, SMBFileSystem> fileSystemCache;

	public static void setClientFactory(Function<Map<String, ?>, SMBClient> clientFactory) {
		SMBFileSystemProvider.clientFactory = clientFactory;
	}

	public static void setAuthenticationContextFactory(
			Function<Map<String, ?>, AuthenticationContext> authenticationContextFactory) {
		SMBFileSystemProvider.authenticationContextFactory = authenticationContextFactory;
	}

	public static void setClientWrapperFactory(Function<Map<String, ?>, SMBClientWrapper> clientWrapperFactory) {
		SMBFileSystemProvider.clientWrapperFactory = clientWrapperFactory;
	}

	public SMBFileSystemProvider() {
		this.fileSystemCache = new ConcurrentHashMap<>();
	}

	@Override
	public String getScheme() {
		return SMBFileSystem.SMB_SCHEME;
	}

	@Override
	public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
		return lookupOrCreateFileSystem(uri, false, true);
	}

	@Override
	public FileSystem getFileSystem(URI uri) {
		return lookupOrCreateFileSystem(uri, true, false);
	}

	@Override
	public Path getPath(URI uri) {
		int indexOfSecondPathSeperator = uri.getPath().indexOf(SMBFileSystem.PATH_SEPARATOR, 1);
		String path = indexOfSecondPathSeperator == -1 ? SMBFileSystem.PATH_SEPARATOR
				: uri.getPath().substring(indexOfSecondPathSeperator);
		return new SMBPath(lookupOrCreateFileSystem(uri, false, false), path);
	}

	private SMBFileSystem lookupOrCreateFileSystem(URI uri, boolean lookupOnly, boolean createOnly) {
		return this.fileSystemCache.compute(SMBFileSystem.createFQN(uri), (fqn, filesystem) -> {
			if (filesystem == null && lookupOnly) {
				throw new FileSystemNotFoundException("No filesystem for '" + fqn + "' could be found.");
			}
			if (filesystem != null && createOnly) {
				throw new FileSystemAlreadyExistsException("Filesystem for '" + fqn + "' does already exist.");
			}
			return filesystem == null ? createFileSystem(fqn) : filesystem;
		});
	}

	private SMBFileSystem createFileSystem(URI fqn) {
		SMBFileSystem.LOGGER.info("creating new filesystem with fqn={}", fqn);
		HashMap<String, Object> newEnv = new HashMap<>(System.getenv());
		System.getProperties().forEach((key, value) -> newEnv.put(String.valueOf(key), value));
		newEnv.put(PROPERTY_FQN, fqn);
		SMBClientWrapper smbClientWrapper = clientWrapperFactory.apply(newEnv);
		return new SMBFileSystem(this, fqn, smbClientWrapper);
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
		SMBFileSystem.LOGGER.debug("newByteChannel path={}, options={}, attrs={}", path, options, attrs);

		return new SMBSeekableByteChannel(SMBPath.fromPath(path), options, attrs);
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter)
			throws IOException {
		SMBFileSystem.LOGGER.debug("newDirectoryStream dir={}, filter={}", dir, filter);

		return new SMBDirectoryStream(SMBPath.fromPath(dir), filter);
	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		SMBFileSystem.LOGGER.debug("createDirectory dir={}, attrs={}", dir, attrs);

		SMBPath.fromPath(dir).call((share, relativePath) -> {
			try (Directory directory = share.openDirectory(relativePath, EnumSet.of(AccessMask.GENERIC_WRITE), null,
					SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_CREATE, null)) {
				return Void.TYPE;
			}
		});
	}

	@Override
	public void delete(Path path) throws IOException {
		SMBFileSystem.LOGGER.debug("delete path={}", path);

		SMBPath.fromPath(path).call((share, relativePath) -> {
			share.rm(relativePath);
			return Void.TYPE;
		});
	}

	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		SMBFileSystem.LOGGER.debug("copy source={}, target={}, options={}", source, target, options);

		boolean replaceExisting = Stream.of(options).anyMatch(option -> option == StandardCopyOption.REPLACE_EXISTING);
		boolean copyAttributes = Stream.of(options).anyMatch(option -> option == StandardCopyOption.COPY_ATTRIBUTES);

		SMBPath.fromPath(source).call(SMBPath.fromPath(target),
				(share, relativePath, shareOther, relativePathOther) -> {
					try (File file = share.openFile(relativePath, EnumSet.of(AccessMask.GENERIC_READ),
							EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
							EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ), SMB2CreateDisposition.FILE_OPEN,
							EnumSet.noneOf(SMB2CreateOptions.class));
							File fileOther = shareOther.openFile(relativePathOther,
									EnumSet.of(AccessMask.GENERIC_WRITE),
									EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
									EnumSet.of(SMB2ShareAccess.FILE_SHARE_WRITE),
									replaceExisting ? SMB2CreateDisposition.FILE_OVERWRITE_IF
											: SMB2CreateDisposition.FILE_CREATE,
									EnumSet.noneOf(SMB2CreateOptions.class))) {

						try {
							file.remoteCopyTo(fileOther);
						} catch (BufferException exception) {
							throw new FileSystemException(source.toString(), target.toString(), exception.getMessage());
						} catch (SMBApiException e) {
							if (e.getStatus().equals(NtStatus.STATUS_NOT_SUPPORTED)) {
								SMBFileSystem.LOGGER.debug("remote copy unsupported, fallback to streaming");
								try (OutputStream os = fileOther.getOutputStream()) {
									file.read(os);
								}
							} else {
								throw e;
							}
						}

						if (copyAttributes) {
							fileOther.setFileInformation(file.getFileInformation(FileBasicInformation.class));
						}

						return Void.TYPE;
					}
				});

	}

	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		SMBFileSystem.LOGGER.debug("move source={}, target={}, options={}", source, target, options);

		boolean replaceExisting = Stream.of(options).anyMatch(option -> option == StandardCopyOption.REPLACE_EXISTING);
		boolean copyAttributes = Stream.of(options).anyMatch(option -> option == StandardCopyOption.COPY_ATTRIBUTES);

		SMBPath.fromPath(source).call(SMBPath.fromPath(target),
				(share, relativePath, shareOther, relativePathOther) -> {
					if (share.equals(shareOther)) {
						try (File file = share.openFile(relativePath, EnumSet.of(AccessMask.DELETE),
								EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
								EnumSet.of(SMB2ShareAccess.FILE_SHARE_DELETE), SMB2CreateDisposition.FILE_OPEN,
								EnumSet.noneOf(SMB2CreateOptions.class))) {
							file.rename(relativePathOther, replaceExisting);
							return Void.TYPE;
						}
					} else {
						SMBFileSystem.LOGGER.debug("different shares, copy and delete");
						try (File file = share.openFile(relativePath,
								EnumSet.of(AccessMask.GENERIC_READ, AccessMask.DELETE),
								EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
								EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_DELETE),
								SMB2CreateDisposition.FILE_OPEN, EnumSet.noneOf(SMB2CreateOptions.class));
								File fileOther = shareOther.openFile(relativePathOther,
										EnumSet.of(AccessMask.GENERIC_WRITE),
										EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
										EnumSet.of(SMB2ShareAccess.FILE_SHARE_WRITE),
										replaceExisting ? SMB2CreateDisposition.FILE_OVERWRITE_IF
												: SMB2CreateDisposition.FILE_CREATE,
										EnumSet.noneOf(SMB2CreateOptions.class))) {

							try {
								file.remoteCopyTo(fileOther);
							} catch (BufferException exception) {
								throw new FileSystemException(source.toString(), target.toString(),
										exception.getMessage());
							} catch (SMBApiException e) {
								SMBFileSystem.LOGGER.debug("remote copy unsupported, fallback to streaming");
								try (OutputStream os = fileOther.getOutputStream()) {
									file.read(os);
								}
							}

							if (copyAttributes) {
								fileOther.setFileInformation(file.getFileInformation(FileBasicInformation.class));
							}

							file.deleteOnClose();
							return Void.TYPE;
						}
					}

				});
	}

	@Override
	public boolean isSameFile(Path path1, Path path2) throws IOException {
		SMBFileSystem.LOGGER.debug("isSameFile path1={}, path2={}", path1, path2);

		Path smbFile1 = SMBPath.fromPath(path1).toAbsolutePath();
		Path smbFile2 = SMBPath.fromPath(path2).toAbsolutePath();
		return smbFile1.equals(smbFile2);
	}

	@Override
	public boolean isHidden(Path path) throws IOException {
		SMBFileSystem.LOGGER.debug("isHidden path={}", path);

		FileBasicInformation fileBasicInformation = SMBPath.fromPath(path)
				.call((share, relativePath) -> share.getFileInformation(relativePath, FileBasicInformation.class));
		return (fileBasicInformation.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_HIDDEN.getValue()) != 0;
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		SMBFileSystem.LOGGER.debug("checkAccess path={}, modes={}", path, modes);

		Set<AccessMask> accessMask = new HashSet<>();
		for (AccessMode mode : modes) {
			if (mode.equals(AccessMode.READ)) {
				accessMask.add(AccessMask.GENERIC_READ);
			} else if (mode.equals(AccessMode.WRITE)) {
				accessMask.add(AccessMask.GENERIC_WRITE);
			} else if (mode.equals(AccessMode.EXECUTE)) {
				accessMask.add(AccessMask.GENERIC_EXECUTE);
			}
		}

		SMBPath.fromPath(path).call((share, relativePath) -> {
			try (DiskEntry entry = share.open(relativePath, accessMask, EnumSet.of(FILE_ATTRIBUTE_NORMAL),
					SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, EnumSet.noneOf(SMB2CreateOptions.class))) {
				return Void.TYPE;
			}
		});
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		SMBFileSystem.LOGGER.debug("getFileAttributeView path={}, type={}, options={}", path, type, options);

		return type.isAssignableFrom(SMBFileAttributeView.class)
				? type.cast(new SMBFileAttributeView(SMBPath.fromPath(path)))
				: null;
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
			throws IOException {
		SMBFileSystem.LOGGER.debug("readAttributes path={}, type={}, options={}", path, type, options);

		return type.isAssignableFrom(SMBFileAttributes.class) ? type.cast(new SMBFileAttributes(SMBPath.fromPath(path)))
				: null;
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
		SMBFileSystem.LOGGER.debug("readAttributes path={}, attributes={}, options={}", path, attributes, options);

		throw new UnsupportedOperationException(
				"Only BasicFileAttributes are currently supported by SMBFileSystemProvider.");
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
		SMBFileSystem.LOGGER.debug("setAttribute path={}, attribute={}, value={}, options={}", path, attribute, value,
				options);

		throw new UnsupportedOperationException(
				"Setting file attributes is currently not supported by SMBFileSystemProvider.");
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		SMBFileSystem.LOGGER.debug("getFileStore path={}", path);

		return new SMBFileStore(SMBPath.fromPath(path));
	}

}
