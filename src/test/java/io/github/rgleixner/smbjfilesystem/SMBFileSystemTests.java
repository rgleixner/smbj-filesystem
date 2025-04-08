package io.github.rgleixner.smbjfilesystem;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class SMBFileSystemTests {

	@Test
	public void testInstance() {
		Path path = Path.of(URI.create("smb://host/share/path/sub"));
		Assertions.assertThat(path).isInstanceOf(SMBPath.class);
	}

	@Test
	public void testIterable() {
		Path path = Path.of(URI.create("smb://host/share/path/sub"));
		List<Path> list = new ArrayList<>();
		path.iterator().forEachRemaining(list::add);

		Assertions.assertThat(list).extracting(Path::toString).containsExactly("path", "sub");
	}

	@Test
	public void testSubpath() {
		Path path = Path.of(URI.create("smb://host/share/path/sub")).subpath(1, 2);
		Assertions.assertThat(path.toString()).isEqualTo("sub");

		List<Path> list = new ArrayList<>();
		path.iterator().forEachRemaining(list::add);

		Assertions.assertThat(list).extracting(Path::toString).containsExactly("sub");
	}

	@Test
	public void testUncPath() {
		Path path = Path.of(URI.create("smb://host/share/path/sub"));
		Assertions.assertThat(((SMBPath) path).toUncPath()).isEqualTo("\\\\host\\share\\path\\sub");
	}

}
