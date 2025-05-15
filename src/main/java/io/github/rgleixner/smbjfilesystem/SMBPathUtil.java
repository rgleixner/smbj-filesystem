package io.github.rgleixner.smbjfilesystem;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;

public final class SMBPathUtil {

	private SMBPathUtil() {
	}

	public static boolean isFolder(String path) {
		return path.endsWith(SMBFileSystem.PATH_SEPARATOR);
	}

	public static boolean isAbsolutePath(String path) {
		return path.startsWith(SMBFileSystem.PATH_SEPARATOR);
	}

	public static boolean isRelativePath(String path) {
		return !path.startsWith(SMBFileSystem.PATH_SEPARATOR);
	}

	public static String[] splitPath(String path) {
		String[] split = path.split(SMBFileSystem.PATH_SEPARATOR);
		if (split.length > 0 && split[0].equals("")) {
			String[] truncated = new String[split.length - 1];
			System.arraycopy(split, 1, truncated, 0, split.length - 1);
			return truncated;
		} else {
			return split;
		}
	}

	public static String mergePath(String[] components, int start, int end, boolean absolute, boolean folder) {
		if (components.length == 0) {
			return "";
		}

		StringBuilder builder = new StringBuilder();
		if (absolute) {
			builder.append(SMBFileSystem.PATH_SEPARATOR);
		}
		for (int i = start; i < end; i++) {
			String component = components[i];
			builder.append(component);
			if (!component.endsWith("/")) {
				builder.append(SMBFileSystem.PATH_SEPARATOR);
			}
		}
		if (!folder) {
			return builder.substring(0, Math.max(0, builder.length() - 1));
		} else {
			return builder.toString();
		}
	}

	public static URI createSmbUriFromUNC(String path, Charset charset) {
		return URI.create("smb:" + encodePath(path.replace("\\", "/"), charset));
	}

	public static String encodePath(String path, Charset charset) {
		StringBuilder sb = new StringBuilder();
		String[] components = path.split("/");
		for (String component : components) {
			sb.append(encodeURIComponent(component, charset)).append("/");
		}
		if (!path.endsWith("/")) {
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.toString();
	}

	public static String encodeURIComponent(String component, Charset charset) {
		return URLEncoder.encode(component, charset).replace("+", "%20").replace("%21", "!").replace("%27", "'")
				.replace("%28", "(").replace("%29", ")").replace("%7E", "~");
	}

	public static String createUNCFromSmbUri(URI uri, Charset charset) {
		return decodePath(uri.toString().replace("smb:", "").replace("/", "\\"), charset);
	}

	public static String decodePath(String path, Charset charset) {
		StringBuilder sb = new StringBuilder();
		String[] components = path.split("\\\\");
		for (String component : components) {
			sb.append(decodeURIComponent(component, charset)).append("\\");
		}
		if (!path.endsWith("\\")) {
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.toString();
	}

	public static String decodeURIComponent(String component, Charset charset) {
		return URLDecoder.decode(component, charset);
	}
}
