package io.github.rgleixner.smbjfilesystem;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

public interface SMBClientWrapper extends Closeable {

	public interface SMBShareWrapper extends Closeable {

		DiskShare getSmbShare() throws IOException;
	}

	SMBShareWrapper getShare() throws IOException;

	public final class SMBClientWrapperImpl implements SMBClientWrapper {

		public final class SMBShareWrapperImpl implements SMBShareWrapper {

			private DiskShare diskShare;

			SMBShareWrapperImpl(DiskShare diskShare) {
				this.diskShare = diskShare;
			}

			@Override
			public DiskShare getSmbShare() throws IOException {
				return diskShare;
			}

			@Override
			public void close() throws IOException {
				//
			}

		}

		private String host;
		private int port;
		private String shareName;
		private SMBClient client;
		private AuthenticationContext authenticationContext;

		private volatile DiskShare share;

		public SMBClientWrapperImpl(URI uri, SMBClient client, AuthenticationContext authenticationContext) {
			this.host = uri.getHost();
			this.port = uri.getPort();
			this.shareName = uri.getPath().substring(1);
			this.client = client;
			this.authenticationContext = authenticationContext;
		}

		@Override
		public SMBShareWrapper getShare() throws IOException {
			if (share == null || !share.isConnected()) {
				synchronized (this) {
					if (share == null || !share.isConnected()) {
						client.close();
						Connection connection = port == -1 ? client.connect(host) : client.connect(host, port);
						Session session = connection.authenticate(authenticationContext);
						share = (DiskShare) session.connectShare(shareName);
					}
				}
			}
			return new SMBShareWrapperImpl(share);
		}

		@Override
		public void close() {
			client.close();
		}

	}

}
