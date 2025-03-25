package io.github.rgleixner.smbjfilesystem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;

public final class SMBDirectoryStream implements DirectoryStream<Path> {

	private ArrayList<Path> content = new ArrayList<>();

	private volatile boolean open = true;

	SMBDirectoryStream(SMBPath path, Filter<? super Path> filter) throws IOException {
		List<FileIdBothDirectoryInformation> list = path.call((share, relativePath) -> share.list(relativePath));
		for (FileIdBothDirectoryInformation name : list) {
			final Path child = path.resolve(name.getFileName());
			if (filter == null || filter.accept(child)) {
				this.content.add(child);
			}
		}
	}

	@Override
	public synchronized Iterator<Path> iterator() {
		if (!open) {
			throw new IllegalStateException("The SMBDirectoryStream has already returned an iterator or was closed.");
		}
		return this.content.iterator();
	}

	@Override
	public synchronized void close() {
		this.open = false;
	}

}
