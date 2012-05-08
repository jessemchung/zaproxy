package org.zaproxy.zap;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ZapGetMethod extends GetMethod implements HttpMethod
{
    /**
     * Log object for this class.
     */
    private static final Log LOG = LogFactory.getLog(ZapGetMethod.class);
    
    /**
     * If we have got an <em>Connection: Upgrade</em>,
     * this will be set to the current connection.
     */
	private SocketChannel upgradedChannel;
    
	/**
     * Reads the response from the given {@link HttpConnection connection}.
     *
     * <p>
     * The response is processed as the following sequence of actions:
     *
     * <ol>
     * <li>
     * {@link #readStatusLine(HttpState,HttpConnection)} is
     * invoked to read the request line.
     * </li>
     * <li>
     * {@link #processStatusLine(HttpState,HttpConnection)}
     * is invoked, allowing the method to process the status line if
     * desired.
     * </li>
     * <li>
     * {@link #readResponseHeaders(HttpState,HttpConnection)} is invoked to read
     * the associated headers.
     * </li>
     * <li>
     * {@link #processResponseHeaders(HttpState,HttpConnection)} is invoked, allowing
     * the method to process the headers if desired.
     * </li>
     * <li>
     * {@link #readResponseBody(HttpState,HttpConnection)} is
     * invoked to read the associated body (if any).
     * </li>
     * <li>
     * {@link #processResponseBody(HttpState,HttpConnection)} is invoked, allowing the
     * method to process the response body if desired.
     * </li>
     * </ol>
     *
     * Subclasses may want to override one or more of the above methods to to
     * customize the processing. (Or they may choose to override this method
     * if dramatically different processing is required.)
     * </p>
     *
     * @param state the {@link HttpState state} information associated with this method
     * @param conn the {@link HttpConnection connection} used to execute
     *        this HTTP method
     *
     * @throws IOException if an I/O (transport) error occurs. Some transport exceptions
     *                     can be recovered from.
     * @throws HttpException  if a protocol exception occurs. Usually protocol exceptions 
     *                    cannot be recovered from.
     */
	@Override
    protected void readResponse(HttpState state, HttpConnection conn)
    throws IOException, HttpException {
        LOG.trace("enter HttpMethodBase.readResponse(HttpState, HttpConnection)");
        // Status line & line may have already been received
        // if 'expect - continue' handshake has been used
        ;
        
        while (getStatusLine() == null) {
            readStatusLine(state, conn);
            processStatusLine(state, conn);
            readResponseHeaders(state, conn);
            processResponseHeaders(state, conn);
            
            int status = this.statusLine.getStatusCode();
            if (status == 101) {
            	// Switching Protocols
            	LOG.debug("Switch Protocols"); 
            	// This means the requester has asked the server to switch protocols
            	// and the server is acknowledging that it will do so (e.g.: Upgrade: websocket).
            } else if ((status >= 100) && (status < 200)) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Discarding unexpected response: " + this.statusLine.toString()); 
                }
                this.statusLine = null;
            }
        }
        readResponseBody(state, conn);
        processResponseBody(state, conn);
    }

    /**
     * Check if this connection should be upgraded.
     * 
     * @param conn the connection in question
     * 
     * @return boolean true if we should close the connection.
     */
    protected boolean shouldCloseConnection(HttpConnection conn) {
        LOG.trace("enter ZapGetMethod.shouldCloseConnection()");
        
    	boolean shouldClose = super.shouldCloseConnection(conn);
    	
		// ensure that it does not want to close a "Connection: upgrade"
		Header connectionHeader = getResponseHeaderGroup().getFirstHeader("connection");
		
		if (this.statusLine.getStatusCode() == 101) {
			// Switching Protocols
			
			if (connectionHeader != null && connectionHeader.getValue().equalsIgnoreCase("upgrade")) {
	            LOG.debug("Got an Upgrade-Request. Retrieve socket channel and do not close connection!");
	            
	            if (conn instanceof ZapHttpConnection) {
	            	upgradedChannel = ((ZapHttpConnection) conn).getSocketChannel();
	            	conn.setHttpConnectionManager(null); // avoid releasing connection
	            }
	            
	        	return false;
			}
		}
    		
    	return shouldClose;
    }

    /**
     * If this response included the header <em>Connection: Upgrade</em>,
     * then this method provides the corresponding connection.
     * 
     * @return Upgraded {@link SocketChannel} or <code>null</code>
     */
	public SocketChannel getUpgradedChannel() {
		return upgradedChannel;
	}
}
