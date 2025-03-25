package io.github.rgleixner.smbjfilesystem;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import com.hierynomus.msfscc.fileinformation.FileBasicInformation;

public final class SMBFileAttributeView implements BasicFileAttributeView {

	static final String FILE_ATTRIBUTE_VIEW_NAME = "basic";

	private final SMBPath path;

	SMBFileAttributeView(SMBPath path) {
		this.path = path;
	}

	@Override
	public String name() {
		return SMBFileAttributeView.FILE_ATTRIBUTE_VIEW_NAME;
	}

	@Override
	public BasicFileAttributes readAttributes() throws IOException {
		return new SMBFileAttributes(this.path);
	}

	@Override
	public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
		if (lastModifiedTime == null && lastAccessTime == null && createTime == null) {
			return;
		}

		FileBasicInformation fileBasicInformaion = new FileBasicInformation(convertFileTime(createTime),
				convertFileTime(lastAccessTime), convertFileTime(lastModifiedTime), convertFileTime(lastModifiedTime),
				0);

		this.path.call((share, relativePath) -> {
			share.setFileInformation(relativePath, fileBasicInformaion);
			return Void.TYPE;
		});
	}

	private com.hierynomus.msdtyp.FileTime convertFileTime(FileTime fileTime) {
		return fileTime == null ? FileBasicInformation.DONT_SET
				: com.hierynomus.msdtyp.FileTime.ofEpochMillis(fileTime.toMillis());
	}
}
