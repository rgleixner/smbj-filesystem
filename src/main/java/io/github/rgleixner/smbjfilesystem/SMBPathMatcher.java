package io.github.rgleixner.smbjfilesystem;

import java.nio.file.Path;
import java.nio.file.PathMatcher;

public final class SMBPathMatcher implements PathMatcher {

	private final String pattern;

	SMBPathMatcher(String pattern) {
		if (pattern.startsWith("glob:")) {
			this.pattern = globToRegex(pattern.replaceFirst("glob:", ""));
		} else if (pattern.startsWith("regex:")) {
			this.pattern = pattern.replaceFirst("regex:", "");
		} else {
			this.pattern = pattern;
		}
	}

	private static String globToRegex(String globPattern) {
		globPattern = globPattern.trim();
		if (globPattern.endsWith("*")) {
			globPattern = globPattern.substring(0, globPattern.length() - 1);
		}
		StringBuilder sb = new StringBuilder(globPattern.length());

		boolean escaping = false;
		int inCurlies = 0;
		for (char currentChar : globPattern.toCharArray()) {
			switch (currentChar) {
			case '*':
				if (escaping) {
					sb.append("\\*");
				} else {
					sb.append(".*");
				}
				escaping = false;
				break;
			case '?':
				if (escaping) {
					sb.append("\\?");
				} else {
					sb.append('.');
				}
				escaping = false;
				break;
			case '.':
			case '(':
			case ')':
			case '+':
			case '|':
			case '^':
			case '$':
			case '@':
			case '%':
				sb.append('\\');
				sb.append(currentChar);
				escaping = false;
				break;
			case '\\':
				if (escaping) {
					sb.append("\\\\");
					escaping = false;
				} else {
					escaping = true;
				}
				break;
			case '{':
				if (escaping) {
					sb.append("\\{");
				} else {
					sb.append('(');
					inCurlies++;
				}
				escaping = false;
				break;
			case '}':
				if (inCurlies > 0 && !escaping) {
					sb.append(')');
					inCurlies--;
				} else if (escaping) {
					sb.append("\\}");
				} else {
					sb.append("}");
				}
				escaping = false;
				break;
			case ',':
				if (inCurlies > 0 && !escaping) {
					sb.append('|');
				} else if (escaping) {
					sb.append("\\,");
				} else {
					sb.append(",");
				}
				break;
			default:
				escaping = false;
				sb.append(currentChar);
			}
		}
		return sb.toString();
	}

	@Override
	public boolean matches(Path path) {
		return path.normalize().toString().matches(this.pattern);
	}
}
