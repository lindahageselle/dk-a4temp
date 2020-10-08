package no.ntnu.datakomm.chat;

import java.io.*;
import java.net.*;
import java.util.LinkedList;
import java.util.List;


public class TCPClient {

    private BufferedReader fromServer;
    private Socket connection;
    private InputStream input;
    private OutputStream output;
    private String lastError = "";
    private final List<ChatListener> listeners = new LinkedList<>();

    /**
     * Connect to a chat server.
     *
     * @param host host name or IP address of the chat server
     * @param port TCP port of the chat server
     * @return True on success, false otherwise
     */
    public boolean connect(String host, int port) {

        boolean connected = false;
        try {
            connection = new Socket(host, port);
            System.out.println("Connected!");
            input = connection.getInputStream();
            output = connection.getOutputStream();
            connected = true;
        }catch (IOException e) {
            lastError = e.getMessage();
            System.out.println("Socket error: " + lastError);
        }
        return connected;
    }

    /**
     * Close the socket. This method must be synchronized, because several
     * threads may try to call it. For example: When "Disconnect" button is
     * pressed in the GUI thread, the connection will get closed. Meanwhile, the
     * background thread trying to read server's response will get error in the
     * input stream and may try to call this method when the socket is already
     * in the process of being closed. with "synchronized" keyword we make sure
     * that no two threads call this method in parallel.
     */
    public synchronized void disconnect() {

        if (isConnectionActive()) {
            try {
                connection.close();
                connection = null;
                onDisconnect();
                System.out.println("Disconnect successful.");
            } catch (IOException e) {
                lastError = e.getMessage();
                System.out.println("Disconnect error: " + lastError);
            }
        }
    }

    /**
     * @return true if the connection is active (opened), false if not.
     */
    public boolean isConnectionActive() {
        return connection != null;
    }

    /**
     * Send a command to server.
     *
     * @param cmd A command. It should include the command word and optional attributes, according to the protocol.
     * @return true on success, false otherwise
     */
    private boolean sendCommand(String cmd) {

        boolean commandSent = false;
        try {
            output.write(cmd.getBytes());
            commandSent = true;
        } catch (IOException e) {
            lastError = e.getMessage();
            System.out.println("Send command error: " + lastError);
        }
        return commandSent;
    }

    /**
     * Send a public message to all the recipients.
     *
     * @param message Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPublicMessage(String message) {

        boolean msgSent = false;
        try {
            msgSent = sendCommand("msg " + message + "\n");
        } catch (Exception e) {
            lastError = e.getMessage();
            System.out.println("Send public message error: " + lastError);
        }
        return msgSent;
    }

    /**
     * Send a login request to the chat server.
     *
     * @param username Username to use
     */
    public void tryLogin(String username) {

        try {
            sendCommand("login " + username + "\n");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Send a request for latest user list to the server. To get the new users,
     * clear your current user list and use events in the listener.
     */
    public void refreshUserList() {

           try{
               sendCommand("users\n" );
               startListenThread();
           }
            catch (Exception e){
               System.out.println(e.getMessage());
            }

    }

    /**
     * Send a private message to a single recipient.
     *
     * @param recipient username of the chat user who should receive the message
     * @param message   Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPrivateMessage(String recipient, String message) {

        boolean msgSent = false;
        try {
            msgSent = sendCommand("privmsg " + recipient + " " + message + "\n");
        } catch (Exception e) {
            lastError = e.getMessage();
            System.out.println("Send private message error: " + lastError);
        }
        return msgSent;
    }

    /**
     * Send a request for the list of commands that server supports.
     */
    public void askSupportedCommands() {

        sendCommand("help \n");
    }

    /**
     * Wait for chat server's response
     *
     * @return one line of text (one command) received from the server
     */
    private String waitServerResponse() {

        String messageFromServer;
        try {
            fromServer = new BufferedReader(new InputStreamReader(input));
            messageFromServer = fromServer.readLine();
            if (messageFromServer == null) {
                connection.close();
                connection = null;
                messageFromServer = "";
            }
            return messageFromServer;
        } catch (IOException e) {
            if (isConnectionActive()) {
                lastError = e.getMessage();
                System.out.println("Wait for server response error: " + lastError);
            }
        }
        return "";
    }

    /**
     * Get the last error message
     *
     * @return Error message or "" if there has been no error
     */
    public String getLastError() {
        return lastError;
    }

    /**
     * Start listening for incoming commands from the server in a new CPU thread.
     */
    public void startListenThread() {

        Thread t = new Thread(this::parseIncomingCommands);
        t.start();
    }

    /**
     * Read incoming messages one by one, generate events for the listeners. A loop that runs until
     * the connection is closed.
     */
    private void parseIncomingCommands() {
        while (isConnectionActive()) {

            String[] arg = waitServerResponse().split(" ", 2);
            String serverCommand = arg[0];
            String serverArgument = null;

            if (arg.length > 1)
                serverArgument = arg[1];

            if (serverCommand != null)
                switch (serverCommand){
                    case "loginok":
                        onLoginResult(true, serverArgument);
                        break;

                    case  "loginerr":
                        onLoginResult(false,serverArgument);
                        break;
                    case "users":
                        if (serverArgument != null){
                        String[] users = serverArgument.split(" ");
                        this.onUsersList(users);}
                        break;

                    case  "msgok":
                        break;

                    case "msgerr":
                        onMsgError(serverArgument);
                        break;

                    case "":
                        break;

                    case "msg":
                        String[] serverArgsBits = serverArgument.split(" ", 2);
                        onMsgReceived(false, serverArgsBits[0],serverArgsBits[1]);
                        break;

                    case "privmsg":
                        assert serverArgument != null;
                        String[] serverArgsBitsPriv = serverArgument.split(" ", 2);
                        onMsgReceived(true, serverArgsBitsPriv[0], serverArgsBitsPriv[1]);
                        break;
                    case "supported":
                        this.onSupported(serverArgument.split(" "));
                        break;

                    default:
                        System.out.println(serverArgument +": "+ serverArgument);

                }
            }
    }

    /**
     * Register a new listener for events (login result, incoming message, etc)
     *
     * @param listener user listening to chat server
     */
    public void addListener(ChatListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Unregister an event listener
     *
     * @param listener user listening to chat server
     */
    public void removeListener(ChatListener listener) {
        listeners.remove(listener);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // The following methods are all event notifiers - notify all the listeners about a specific event.
    // By "event" here we mean "information received from the chat server".
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Notify listeners that login operation is complete (either with success or
     * failure)
     *
     * @param success When true, login successful. When false, it failed
     * @param errMsg  Error message if any
     */
    private void onLoginResult(boolean success, String errMsg) {
        for (ChatListener l : listeners) {
            l.onLoginResult(success, errMsg);
        }
    }

    /**
     * Notify listeners that socket was closed by the remote end (server or
     * Internet error)
     */
    private void onDisconnect() {
        for (ChatListener l : listeners) {
            l.onDisconnect();
        }
    }

    /**
     * Notify listeners that server sent us a list of currently connected users
     *
     * @param users List with usernames
     */
    private void onUsersList(String[] users) {
        for (ChatListener l : listeners) {
            l.onUserList(users);
        }
    }

    /**
     * Notify listeners that a message is received from the server
     *
     * @param priv   When true, this is a private message
     * @param sender Username of the sender
     * @param text   Message text
     */
    private void onMsgReceived(boolean priv, String sender, String text) {
        for (ChatListener l : listeners) {
            l.onMessageReceived(new TextMessage(sender, priv, text));
        }
    }

    /**
     * Notify listeners that our message was not delivered
     *
     * @param errMsg Error description returned by the server
     */
    private void onMsgError(String errMsg) {
        for (ChatListener l : listeners) {
            l.onMessageError(errMsg);
        }
    }

    /**
     * Notify listeners that command was not understood by the server.
     *
     * @param errMsg Error message
     */
    private void onCmdError(String errMsg) {
        for (ChatListener l : listeners) {
            l.onCommandError(errMsg);
        }
    }

    /**
     * Notify listeners that a help response (supported commands) was received
     * from the server
     *
     * @param commands Commands supported by the server
     */
    private void onSupported(String[] commands) {
        for (ChatListener l : listeners) {
            l.onSupportedCommands(commands);
        }
    }
}
