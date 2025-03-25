package io.github.rgleixner.smbjfilesystem;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileAllInformation;

public final class SMBFileAttributes implements BasicFileAttributes {

	private final FileAllInformation fileInformation;

	SMBFileAttributes(SMBPath path) throws IOException {
		fileInformation = path.call((share, relativePath) -> share.getFileInformation(relativePath));
	}

	@Override
	public FileTime lastModifiedTime() {
		return FileTime.from(fileInformation.getBasicInformation().getLastWriteTime().toEpochMillis(),
				TimeUnit.MILLISECONDS);
	}

	@Override
	public FileTime lastAccessTime() {
		return FileTime.from(fileInformation.getBasicInformation().getLastAccessTime().toEpochMillis(),
				TimeUnit.MILLISECONDS);
	}

	@Override
	public FileTime creationTime() {
		return FileTime.from(fileInformation.getBasicInformation().getCreationTime().toEpochMillis(),
				TimeUnit.MILLISECONDS);
	}

	@Override
	public boolean isRegularFile() {
		return (fileInformation.getBasicInformation().getFileAttributes()
				& FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) == 0;
	}

	@Override
	public boolean isDirectory() {
		return (fileInformation.getBasicInformation().getFileAttributes()
				& FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
	}

	@Override
	public boolean isSymbolicLink() {
		return false;
	}

	@Override
	public boolean isOther() {
		return false;
	}

	@Override
	public long size() {
		return fileInformation.getStandardInformation().getEndOfFile();
	}

	@Override
	public Object fileKey() {
		return fileInformation.getNameInformation();
	}

}
