package io.github.rgleixner.smbjfilesystem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.EnumSet;
import java.util.Set;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileStandardInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.share.File;

public final class SMBSeekableByteChannel implements SeekableByteChannel {

	private final File file;

	private volatile boolean open = true;

	private volatile long position = 0;

	SMBSeekableByteChannel(SMBPath path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {

		if (options.contains(StandardOpenOption.DSYNC) || options.contains(StandardOpenOption.SYNC)
				|| options.contains(StandardOpenOption.SPARSE)
				|| options.contains(StandardOpenOption.DELETE_ON_CLOSE)) {
			throw new UnsupportedOperationException(
					"SMBFileSystemProvider does not support the options SYNC, DSYNC, SPARSE, DELETE_ON_CLOSE");
		}

		this.file = path.getFileSystem()
				.call(path,
						(share, relativePath) -> share.openFile(relativePath,
								options.contains(StandardOpenOption.WRITE) ? EnumSet.of(AccessMask.GENERIC_WRITE)
										: EnumSet.of(AccessMask.GENERIC_READ),
								EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
								options.contains(StandardOpenOption.WRITE) ? EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ)
										: EnumSet.of(SMB2ShareAccess.FILE_SHARE_WRITE),
								options.contains(StandardOpenOption.CREATE_NEW) ? SMB2CreateDisposition.FILE_CREATE
										: (options.contains(StandardOpenOption.CREATE)
												? SMB2CreateDisposition.FILE_OPEN_IF
												: SMB2CreateDisposition.FILE_OPEN),
								EnumSet.noneOf(SMB2CreateOptions.class)));

		if (options.contains(StandardOpenOption.WRITE)) {
			if (options.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
				this.truncate(0);
			}
			if (options.contains(StandardOpenOption.APPEND)) {
				this.position(this.size());
			}
		}
	}

	@Override
	public synchronized int read(ByteBuffer dst) throws IOException {
		if (!this.open) {
			throw new ClosedChannelException();
		}
		long offset = this.file.read(dst, position);
		position += offset;
		return (int) offset;
	}

	@Override
	public synchronized int write(ByteBuffer src) throws IOException {
		if (!this.open) {
			throw new ClosedChannelException();
		}
		long offset = this.file.write(src, position);
		position += offset;
		return (int) offset;
	}

	@Override
	public synchronized long position() throws IOException {
		if (!this.open) {
			throw new ClosedChannelException();
		}
		return this.position;
	}

	@Override
	public synchronized long size() throws IOException {
		if (!this.open) {
			throw new ClosedChannelException();
		}
		return this.file.getFileInformation(FileStandardInformation.class).getEndOfFile();
	}

	@Override
	public synchronized SeekableByteChannel position(long newPosition) throws IOException {
		if (!this.open) {
			throw new ClosedChannelException();
		}
		this.position = newPosition;
		return this;
	}

	@Override
	public synchronized SeekableByteChannel truncate(long size) throws IOException {
		if (!this.open) {
			throw new ClosedChannelException();
		}
		this.file.setLength(size);
		this.position = Math.min(this.position, size);
		return this;
	}

	@Override
	public synchronized boolean isOpen() {
		return this.open;
	}

	@Override
	public synchronized void close() throws IOException {
		if (this.open) {
			this.open = false;
			this.file.close();
		}
	}
}
