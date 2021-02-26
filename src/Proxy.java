import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;


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
    //static HashMap<String, String> blockedSites;

    public static HashSet<String> blockedSites;

    // store all the threads that currently running
    static ArrayList<Thread> threadWorkers;



    public Proxy(int port) {

        // Load in hash map containing previously cached sites
        cache = new HashMap<>();

        // LRUCache capacity
        int LRUCapacity = 2;
        lruCache = new LRUCache(LRUCapacity);

        // cache blocked sites
        blockedSites = new HashSet<>();

        // Create array list to hold servicing threads
        threadWorkers = new ArrayList<>();


        new Thread(this).start();

        try{
            // Load in cached sites from file
            File cachedSites = new File("cachedSites.txt");
            if(!cachedSites.exists()){
                System.out.println("No cached sites found - creating new file");
                cachedSites.createNewFile();
            } else {
                //LoadData.readCachedSites();
            }

            // Load in blocked sites from file
            File blockedSitesTxtFile = new File("blockedSites.txt");
            if(!blockedSitesTxtFile.exists()){
                System.out.println("No default blocked sites");
                blockedSitesTxtFile.createNewFile();
            } else {
                LoadData.readBlockedSites();
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


    // save cached file for further use
    private void closeServer(){
        isRunning = false;
        try{
            FileOutputStream fileOutputStream = new FileOutputStream("cachedSites.txt");
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);

            objectOutputStream.writeObject(cache);
            objectOutputStream.close();
            fileOutputStream.close();

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






    // if the prefix stored in blocked Sites match with url, then block it
    public static boolean isBlocked (String url){
        for (String s: blockedSites) {
            if (url.startsWith(s)) {
                return true;
            }
        }
        return false;
    }




   // user interface to add blocked sites
    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);

        String userInput;
        while(isRunning){
            System.out.println("Please enter websites you want to block");
            userInput = scanner.nextLine();
            if(userInput.equals("blocked")) {
                for(String s: blockedSites) {
                    System.out.println(s);
                }
            } else if(userInput.equals("cached")) {
                lruCache.iterate();
            }
            else {
                if(userInput.startsWith("http")) {
                    blockedSites.add(userInput);
                }

            }

        }
        scanner.close();
    }

}