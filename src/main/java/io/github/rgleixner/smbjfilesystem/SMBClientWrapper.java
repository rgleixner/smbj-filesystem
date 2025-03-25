package io.github.rgleixner.smbjfilesystem;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

public final class SMBClientWrapper implements Closeable {

	private String host;
	private int port;
	private String shareName;
	private SMBClient client;
	private AuthenticationContext authenticationContext;

	private Connection connection;
	private Session session;
	private DiskShare share;

	public SMBClientWrapper(URI uri, SMBClient client, AuthenticationContext authenticationContext) {
		this.host = uri.getHost();
		this.port = uri.getPort();
		this.shareName = uri.getPath().substring(1);
		this.client = client;
		this.authenticationContext = authenticationContext;
	}

	DiskShare getShare() throws IOException {
		if (share == null) {
			connection = port == -1 ? client.connect(host) : client.connect(host, port);
			session = connection.authenticate(authenticationContext);
			share = (DiskShare) session.connectShare(shareName);
		}
		return share;
	}

	@Override
	public void close() throws IOException {
		share.close();
		session.close();
		connection.close();
	}

}
