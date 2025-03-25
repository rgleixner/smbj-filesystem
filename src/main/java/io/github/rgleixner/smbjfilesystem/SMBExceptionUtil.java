package io.github.rgleixner.smbjfilesystem;

import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;

import com.hierynomus.mssmb2.SMBApiException;

public final class SMBExceptionUtil {

	private SMBExceptionUtil() {
	}

	private static FileSystemException translateToNIOException(SMBApiException e, String path, String other) {
		switch (e.getStatus()) {
		case STATUS_FILE_IS_A_DIRECTORY:
		case STATUS_ACCESS_DENIED:
			return new AccessDeniedException(path, other, e.getMessage());
		case STATUS_NO_SUCH_FILE:
		case STATUS_OBJECT_NAME_NOT_FOUND:
		case STATUS_OBJECT_PATH_NOT_FOUND:
		case STATUS_DELETE_PENDING:
			return new NoSuchFileException(path, other, e.getMessage());
		case STATUS_OBJECT_NAME_COLLISION:
			return new FileAlreadyExistsException(path, other, e.getMessage());
		case STATUS_NOT_A_DIRECTORY:
			return new NotDirectoryException(path);
		default:
			return new FileSystemException(path, other, e.getMessage());
		}
	}

	static FileSystemException translateToNIOException(SMBApiException e, Path path, Path other) {
		String a = (path == null) ? null : path.toString();
		String b = (other == null) ? null : other.toString();
		return translateToNIOException(e, a, b);
	}

	static FileSystemException translateToNIOException(SMBApiException e, Path path) {
		return translateToNIOException(e, path, null);
	}
}
