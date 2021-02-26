import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;


public class Proxy implements Runnable{


    // Main method for the program
    public static void main(String[] args) {
        // Create an instance of Proxy and begin listening for connections

        int defaultPort = 80;
        Proxy myProxy = new Proxy(defaultPort);
        myProxy.listen();
    }


    private ServerSocket serverSocket;

    // volatile tells the compiler that the value must not be cached
    private volatile boolean isRunning = true;


    // Key is page or image URL, value is the file requested
    static HashMap<String, File> cache;

    // alternative
    static LRUCache lruCache;

    // blocked sites
    static HashMap<String, String> blockedSites;

    // store all the threads that currently running
    static ArrayList<Thread> threadWorkers;



    public Proxy(int port) {

        // Load in hash map containing previously cached sites
        cache = new HashMap<>();

        // LRUCache capacity
        int LRUCapacity = 2;
        lruCache = new LRUCache(LRUCapacity);

        // cache blocked sites
        blockedSites = new HashMap<>();

        // Create array list to hold servicing threads
        threadWorkers = new ArrayList<>();

        // Start dynamic manager on a separate thread.
        //new Thread(this).start();

        try{
            // Load in cached sites from file
            File cachedSites = new File("cachedSites.txt");
            if(!cachedSites.exists()){
                System.out.println("No cached sites found - creating new file");
                cachedSites.createNewFile();
            } else {
//                FileInputStream fileInputStream = new FileInputStream(cachedSites);
//                System.out.println("reached 2");
//                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
//                System.out.println("reached 2");
//                cache = (HashMap<String,File>)objectInputStream.readObject();
//                System.out.println("reached 2");
//                fileInputStream.close();
//                objectInputStream.close();
            }

            // Load in blocked sites from file
            File blockedSitesTxtFile = new File("blockedSites.txt");
            if(!blockedSitesTxtFile.exists()){
                System.out.println("No blocked sites found - creating new file");
                blockedSitesTxtFile.createNewFile();
            } else {
//                FileInputStream fileInputStream = new FileInputStream(blockedSitesTxtFile);
//                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
//                blockedSites = (HashMap<String, String>)objectInputStream.readObject();
//                fileInputStream.close();
//                objectInputStream.close();
            }
        } catch (IOException e) {
            System.out.println("Error loading previously cached sites file");
            e.printStackTrace();
        }

        try {
            // Create the Server Socket for the Proxy
            serverSocket = new ServerSocket(port);
            System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "..");
            isRunning = true;
        }

        // Catch exceptions associated with opening socket
        catch (SocketException se) {
            System.out.println("Socket Exception when connecting to client");
            se.printStackTrace();
        }
        catch (SocketTimeoutException ste) {
            System.out.println("Timeout occured while connecting to client");
        }
        catch (IOException io) {
            System.out.println("IO exception when connecting to client");
        }
    }


    /**
     * Listens to port and accepts new socket connections.
     * Creates a new thread to handle the request and passes it the socket connection and continues listening.
     */
    public void listen(){

        while(isRunning){
            try {
                // wait until the connection established
                Socket socket = serverSocket.accept();

                // Create new Thread and pass it Runnable RequestHandler
                Thread thread = new Thread(new RequestHandler(socket));

                // Key a reference to each thread so they can be joined later if necessary
                threadWorkers.add(thread);

                thread.start();
            } catch (SocketException e) {
                System.out.println("The proxy is shutting down");
                System.out.println("Server closed");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *  helper functions, updating hashmap
     *  will be used in RequestHandler class
     */

    // add to hashmap
    public static void updateMap(String urlString, File fileToCache){
        //cache.put(urlString, fileToCache);
        lruCache.put(urlString, fileToCache);
    }

    // get file from hashmap
    public static File getFromMap(String url){
        //return cache.get(url);
        return lruCache.get(url);
    }


    /**
     * Saves the blocked and cached sites to a file so they can be re loaded at a later time.
     * Also joins all of the RequestHandler threads currently servicing requests.
     */
    private void closeServer(){
        isRunning = false;
        try{
            FileOutputStream fileOutputStream = new FileOutputStream("cachedSites.txt");
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);

            objectOutputStream.writeObject(cache);
            objectOutputStream.close();
            fileOutputStream.close();
            System.out.println("Cached Sites written");

            FileOutputStream fileOutputStream2 = new FileOutputStream("blockedSites.txt");
            ObjectOutputStream objectOutputStream2 = new ObjectOutputStream(fileOutputStream2);
            objectOutputStream2.writeObject(blockedSites);
            objectOutputStream2.close();
            fileOutputStream2.close();
            System.out.println("Blocked Site list saved");
            try{
                // wait until all workers finish the job
                for(Thread thread : threadWorkers){
                    if(thread.isAlive()){
                        thread.join();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Close Server Socket
        try{
            serverSocket.close();
            System.out.println("Server socket closed");
        } catch (Exception e) {
            e.printStackTrace();
        }

    }






    /**
     * Check if a URL is blocked by the proxy
     * @param url URL to check
     * @return true if URL is blocked, false otherwise
     */
    public static boolean isBlocked (String url){
        if(blockedSites.get(url) != null){
            return true;
        } else {
            return false;
        }
    }




    /**
     * Creates a management interface which can dynamically update the proxy configurations
     * 		blocked : Lists currently blocked sites
     *  	cached	: Lists currently cached sites
     *  	close	: Closes the proxy server
     *  	*		: Adds * to the list of blocked sites
     */
    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);

        String command;
        while(isRunning){
            System.out.println("Enter new site to block, or type \"blocked\" to see blocked sites, \"cached\" to see cached sites, or \"close\" to close server.");
            command = scanner.nextLine();
            if(command.toLowerCase().equals("blocked")){
                System.out.println("\nCurrently Blocked Sites");
                for(String key : blockedSites.keySet()){
                    System.out.println(key);
                }
                System.out.println();
            }

            else if(command.toLowerCase().equals("cached")){
                System.out.println("\nCurrently Cached Sites");
                for(String key : cache.keySet()){
                    System.out.println(key);
                }
                System.out.println();
            }


            else if(command.equals("close")){
                isRunning = false;
                closeServer();
            }


            else {
                blockedSites.put(command, command);
                System.out.println("\n" + command + " blocked successfully \n");
            }
        }
        scanner.close();
    }

}