package io.github.rgleixner.smbjfilesystem;

import java.net.URI;
import java.nio.charset.StandardCharsets;
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
		URI uri = SMBPathUtil.createSmbUriFromUNC("\\\\host\\share\\path\\sub\\!'()~ äöüß", StandardCharsets.UTF_8);
		Assertions.assertThat(uri.toString()).isEqualTo("smb://host/share/path/sub/!'()~%20%C3%A4%C3%B6%C3%BC%C3%9F");

		String unc = SMBPathUtil.createUNCFromSmbUri(
				URI.create("smb://host/share/path/sub/!'()~%20%C3%A4%C3%B6%C3%BC%C3%9F"), StandardCharsets.UTF_8);
		Assertions.assertThat(unc).isEqualTo("\\\\host\\share\\path\\sub\\!'()~ äöüß");

		Path p1 = Path.of(URI.create("smb://host/share/path/sub/!'()~%20äöüß"));
		Path p2 = Path.of(URI.create("smb://host/share/path/sub/%21%27%28%29%7E%20%C3%A4%C3%B6%C3%BC%C3%9F"));

		Assertions.assertThat(p1.toString()).isEqualTo("/path/sub/!'()~ äöüß");
		Assertions.assertThat(p2.toString()).isEqualTo("/path/sub/!'()~ äöüß");

		Assertions.assertThat(p1.toUri().toString())
				.isEqualTo("smb://host/share/path/sub/!'()~%20%C3%A4%C3%B6%C3%BC%C3%9F");
		Assertions.assertThat(p2.toUri().toString())
				.isEqualTo("smb://host/share/path/sub/!'()~%20%C3%A4%C3%B6%C3%BC%C3%9F");
	}

}
