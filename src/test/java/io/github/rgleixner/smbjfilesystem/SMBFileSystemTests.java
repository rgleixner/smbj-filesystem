package io.github.rgleixner.smbjfilesystem;

import java.net.URI;
import java.nio.file.Path;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class SMBFileSystemTests {

	@Test
	public void test() {
		Path path = Path.of(URI.create("smb://host/share/path"));
		Assertions.assertThat(path).isInstanceOf(SMBPath.class);
	}

}
