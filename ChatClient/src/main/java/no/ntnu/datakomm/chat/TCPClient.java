package no.ntnu.datakomm.chat;

import java.io.*;
import java.net.*;
import java.util.LinkedList;
import java.util.List;

public class TCPClient {
    private PrintWriter toServer;
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
        // Step 1: implement this method
        // Hint: Remember to process all exceptions and return false on error
        // Hint: Remember to set up all the necessary input/output stream variables

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
        // Step 4: implement this method
        // Hint: remember to check if connection is active

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
        // Step 2: Implement this method
        // Hint: Remember to check if connection is active

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
        // Step 2: implement this method
        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.

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
        // Step 3: implement this method
        // Hint: Reuse sendCommand() method

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
        // TODO Step 5: implement this method
        // Hint: Use Wireshark and the provided chat client reference app to find out what commands the
        // client and server exchange for user listing.
        //Birger: some of my stuff, no touching
           try{
               sendCommand("users\n" );
               startListenThread();
           }
            catch (Exception e){
               System.out.println(e.getMessage());
            }

//        Wireshark seems to only see command "users", so I would assume:
//        sendCommand("users \n");
    }

    /**
     * Send a private message to a single recipient.
     *
     * @param recipient username of the chat user who should receive the message
     * @param message   Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPrivateMessage(String recipient, String message) {
        // Step 6: Implement this method
        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.

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
        // TODO Step 8: Implement this method
        // Hint: Reuse sendCommand() method
        sendCommand("help \n");
    }

    /**
     * Wait for chat server's response
     *
     * @return one line of text (one command) received from the server
     */
    private String waitServerResponse() {
        // Step 9: implement this method
        // If you get I/O Exception or null from the stream, it means that something has gone wrong
        // with the stream and hence the socket. Probably a good idea to close the socket in that case.
        String messageFromServer = null;
        try {
            fromServer = new BufferedReader(new InputStreamReader(input));
            messageFromServer = fromServer.readLine();
            if (messageFromServer == null) {
                connection.close();
                connection = null;
            }
            return messageFromServer;
        } catch (IOException e) {
            if (isConnectionActive()) {
                lastError = e.getMessage();
                System.out.println("Wait for server response error: " + lastError);
            }
        }
        return null;
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
        // Call parseIncomingCommands() in the new thread.
        Thread t = new Thread(this::parseIncomingCommands);
        t.start();
    }

    /**
     * Read incoming messages one by one, generate events for the listeners. A loop that runs until
     * the connection is closed.
     */
    private void parseIncomingCommands() {
        while (isConnectionActive()) {
            // TODO Step 3: Implement this method
            // Hint: Reuse waitServerResponse() method
            // Hint: Have a switch-case (or other way) to check what type of response is received from the server
            // and act on it.
            // Hint: In Step 3 you need to handle only login-related responses.
            // Hint: In Step 3 reuse onLoginResult() method


            // TODO Step 5: update this method, handle user-list response from the server
            //Birger : messing around here too

            //Birger: Attemt at switch case...... TODO: fILL OUT DESCRIPTION
            String[] arg = waitServerResponse().split(" ", 2);
            String serverCommand = arg[0];
            String serverArgument = null;

            if (arg.length > 1)
                serverArgument = arg[1].toString();

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
                    case "msgerr":
                        break;

                    case "msg":
                        onMsgReceived(false, "some dude",serverArgument);
                        break;

                    case "privmsg":
                        assert serverArgument != null;
                        String[] serverArgsBits = serverArgument.split(" ", 2);
                        onMsgReceived(true, serverArgsBits[1], serverArgsBits[2]);
                        break;
                    case "supported":
                        this.onSupported(serverArgument.split(" "));
                        break;
                    default:
                        System.out.println(serverArgument +": "+ serverArgument);

                }
            }

            // Hint: In Step 5 reuse onUserList() method

            // TODO Step 7: add support for incoming chat messages from other users (types: msg, privmsg)
            // TODO Step 7: add support for incoming message errors (type: msgerr)
            // TODO Step 7: add support for incoming command errors (type: cmderr)
            // Hint for Step 7: call corresponding onXXX() methods which will notify all the listeners

            // TODO Step 8: add support for incoming supported command list (type: supported)

            // Step 3 + some of step 8
            //String receivedResponse = this.waitServerResponse();
            //if (receivedResponse != null) {

                // We can make this into a switch case later if we want.
                // Just did this because it was easy

                //if (receivedResponse.contains("loginok")) {
                  //  onLoginResult(true, "Login successful");
                //}
               // else if (receivedResponse.contains("loginerr")) {
                //    onLoginResult(false, "Login failed. Choose a unique single-word username.");
               // }


               // else if (receivedResponse.contains("msgok")) {
                    //Do nothing, it's fine
                }
               // else if (receivedResponse.contains("msgerr")) {
                //    onMsgError("Something went wrong with the last private message sent from this client");
               // }
               // else if (receivedResponse.contains("supported")) {

//                    TODO how to i get the actual response thoooo ughhhh
//                    This doesn't work
//                    onSupported(new String[] {receivedResponse});




          //  }
       // }


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
