package net.virtualinfinity.telnet;

import net.virtualinfinity.nio.*;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;

/**
 * A helper class to start Telnet sessions which connect to a remote port.
 *
 * @see ConnectionInitiator
 * @author <a href='mailto:Daniel@coloraura.com'>Daniel Pitts</a>
 */
public class ClientStarter {
    private final ConnectionInitiator connectionInitiator;

    /**
     * Create a new client starter with a new ConnectionInitiator.
     */
    public ClientStarter() {
        this(new ConnectionInitiator());
    }

    /**
     * Create a new client starter with the given ConnectionInitiator.
     *
     * @param connectionInitiator the ConnectionInitiator to use.
     */
    public ClientStarter(ConnectionInitiator connectionInitiator) {
        this.connectionInitiator = connectionInitiator;
    }

    /**
     * Connect to the given hostname on the default port (23), using the given event loop for managing async operations.
     *
     * @param loop the EventLoop to use
     * @param hostname the hostname to connect to.
     * @param sessionListener The session listener to be informed of session events.
     *
     * @see #connect(EventLoop, String, int, SessionListener)
     */
    public void connect(EventLoop loop, String hostname, SessionListener sessionListener) {
        connect(loop, hostname, 23, sessionListener);
    }

    /**
     * Connect to the given hostname on the given port, using the given event loop for managing async operations.
     * @param loop the EventLoop to use
     * @param hostname the hostname to connect to.
     * @param port the port number.
     * @param sessionListener The session listener to be informed of session events.
     */
    public void connect(EventLoop loop, String hostname, int port, SessionListener sessionListener) {
        final ConnectionListener connectionListener = new ClientConnectionListener(sessionListener);
        connectionInitiator.connect(loop, hostname, port, connectionListener, socketChannel -> {
                try {
                    final OutputBuffer outputBuffer = new OutputBuffer();
                    final OptionCommandManagerImpl optionManager = new OptionCommandManagerImpl(outputBuffer::append);
                    final OutputChannel outputChannel = new OutputChannel(outputBuffer::append);
                    final SubNegotiationOutputChannel subNegotiationOutputChannel = optionManager.subNegotiationOutputChannel(outputChannel);
                    final Session session = new SessionImpl(optionManager.options(), outputChannel, subNegotiationOutputChannel, socketChannel::close);
                    final CommandRouter commandReceiver = new CommandRouter(sessionListener, new SubNegotiationDataRouterImpl(sessionListener), optionManager);
                    final ClientSessionConnectionListener conListener = new ClientSessionConnectionListener(sessionListener, session);
                    final InputChannelDecoder decoder = new InputChannelDecoder(commandReceiver);
                    final SocketSelectionActions socketSelectionActions = new SocketSelectionActions(socketChannel, conListener, decoder, outputBuffer, 2048, false);

                    socketSelectionActions.register(loop);
                } catch (ClosedChannelException e) {
                    connectionListener.connectionFailed(e);
                }
            });

    }


    /**
     * The connection listener for the connection phase before we've got a session.
     */
    private static class ClientConnectionListener implements ConnectionListener {
        protected final SessionListener sessionListener;

        public ClientConnectionListener(SessionListener sessionListener) {
            this.sessionListener = sessionListener;
        }

        @Override
        public void connecting() {
            sessionListener.connecting();
        }

        @Override
        public void connected() {
        }

        @Override
        public void connectionFailed(IOException e) {
            sessionListener.connectionFailed(e);

        }

        @Override
        public void disconnected() {
            sessionListener.connectionClosed();

        }
    }

    /**
     * The connection listener for the phase after we've got a session.
     */
    private static class ClientSessionConnectionListener extends ClientConnectionListener {
        private final Session session;

        public ClientSessionConnectionListener(SessionListener sessionListener, Session session) {
            super(sessionListener);
            this.session = session;
        }

        @Override
        public void connecting() {
        }

        @Override
        public void connected() {
            sessionListener.connected(session);
        }

    }
}
