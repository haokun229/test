import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

class StagServer
{
    public static void main(String args[])
    {
        if(args.length != 2) System.out.println("Usage: java StagServer <entity-file> <action-file>");
        else new StagServer(args[0], args[1], 8888);
    }


    private StagState stagState;   // game state maintain the game

    public StagServer(String entityFilename, String actionFilename, int portNumber)
    {
        stagState = new StagState(entityFilename, actionFilename);
        try {
            ServerSocket ss = new ServerSocket(portNumber);  // create a socket listen on portNumber
            System.out.println("Server Listening");
            while(true) acceptNextConnection(ss);
        } catch(IOException ioe) {
            System.err.println(ioe);
        }
    }

    private void acceptNextConnection(ServerSocket ss)
    {
        try {
            // Next line will block until a connection is received
            Socket socket = ss.accept();   // wait for next connection
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            processNextCommand(in, out);  // process connection
            out.close();
            in.close();
            socket.close();  // close connection
        } catch(IOException ioe) {
            System.err.println(ioe);
        }
    }

    private void processNextCommand(BufferedReader in, BufferedWriter out) throws IOException
    {
        String line = in.readLine();
        String[] commands = line.split(":");  // split command from user by : to get username
        String username = commands[0];
        Player player = stagState.findPlayer(username);   // user username to find player
        String cmd = commands[1];
        String[] message = cmd.strip().split(" ");   // split command by space to get action and subjects
        String response = "";
        switch (message[0]) {
            case "inventory":
            case "inv":
                response = player.showInventory();    // inv cmd
                break;
            case "get":     // get cmd
                response = stagState.pickUp(player, message[1]);
                break;
            case "drop":    // drop cmd
                response = stagState.drop(player, message[1]);
                break;
            case "goto":   // goto cmd
                response = stagState.gotoPosition(player, message[1]);
                break;
            case "look":  // look cmd
                response = stagState.look(player);
                break;
            case "health": // health cmd
                response = "Your health is " + player.getHealth() + " now";
                break;
            default:   // other action cmd
                response = stagState.processAction(player, message);
        }

        response = "server: \n" + response;
        out.write(response);   // write response to out stream
        out.flush();
    }


}
