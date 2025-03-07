import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import javax.imageio.ImageIO;

public class RequestHandler implements Runnable {

    Socket clientSocket;


    // read data from client to proxy
    private BufferedReader dataReader;

    // send data from proxy to client
    private BufferedWriter dataSender;


    // listen to client request
    private Thread httpsClientToServer;


    // serving HTTP GET request
    public RequestHandler(Socket clientSocket){
        this.clientSocket = clientSocket;
        try{
            final int defaultTimeout = 5000;
            this.clientSocket.setSoTimeout(defaultTimeout);
            dataReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            dataSender = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }



    @Override
    public void run() {

        // Get Request from client
        System.out.println("Start time: " + java.time.LocalDateTime.now());

        String requestString;
        try{
            requestString = dataReader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error reading request from client");
            return;
        }

        // Parse out URL
        if(requestString == null) return;
        System.out.println("Original request: " + requestString);
        // prevent NPE

        // Get the Request type

        int spaceIdx = requestString.indexOf(' ');
        String request = requestString.substring(0,spaceIdx);
        //System.out.println("debug:" + request);

        // remove request type and space
        String urlString = requestString.substring(spaceIdx+1);

        //System.out.println("debug:" + urlString);

        // Remove everything past next space
        // debug, starts from 1 to get rid of /
        urlString = urlString.substring(1, urlString.indexOf(' '));

        System.out.println("actual url: " + urlString);

        // prefix validation
        String prefix = urlString.substring(0,4);
        if(!prefix.equals("http")){
            String temp = "http://";
            urlString = temp + urlString;
        }


        // Check if site is blocked
        if(Proxy.isBlocked(urlString)){
            System.out.println("Blocked site requested : " + urlString);
            blockedSiteRequested();
            return;
        }


        // Check request type
        if(request.equals("CONNECT")){
            System.out.println("HTTPS Request for : " + urlString);
            handleHTTPSRequest(urlString);
        }

        else{ // HTTP GET
            // Check if we have a cached copy
            File file;
            // cached in hashmap
            if((file = Proxy.getFromMap(urlString)) != null){
                System.out.println("Found cached file : " + urlString);
                sendCachedPageToClient(file);
            } else {
                //System.out.println("reached");
                System.out.println("HTTP GET for : " + urlString);
                sendNotCachedToClient(urlString);
            }
        }
    }


    // send cached file to client
    private void sendCachedPageToClient(File cachedFile){
        // Read from File containing cached web page
        try{
            // If file is an image write data to client using buffered image.
            String fileExtension = cachedFile.getName().substring(cachedFile.getName().lastIndexOf('.'));

            // Response that will be sent to the server
            String response;
            if((fileExtension.contains(".png")) || fileExtension.contains(".jpg") ||
                    fileExtension.contains(".jpeg")){
                // Read in image from storage
                BufferedImage image = ImageIO.read(cachedFile);

                if(image == null ){
                    System.out.println("Image " + cachedFile.getName() + " was null");
                    response = "HTTP/1.0 404 NOT FOUND \n" +
                            "Proxy-agent: ProxyServer/1.0\n" +
                            "\r\n";
                    dataSender.write(response);
                    dataSender.flush();
                } else {
                    response = "HTTP/1.0 200 OK\n" +
                            "Proxy-agent: ProxyServer/1.0\n" +
                            "\r\n";
                    dataSender.write(response);
                    dataSender.flush();
                    ImageIO.write(image, fileExtension.substring(1), clientSocket.getOutputStream());
                }
            }

            // Standard text based file requested
            else {
                BufferedReader cachedFileBufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(cachedFile)));

                response = "HTTP/1.0 200 OK\n" +
                        "Proxy-agent: ProxyServer/1.0\n" +
                        "\r\n";
                // give client response
                dataSender.write(response);
                dataSender.flush();

                // give client file content
                String tempBuffer = "";
                while((tempBuffer = cachedFileBufferedReader.readLine()) != null){
                    dataSender.write(tempBuffer);
                }
                dataSender.flush();
                System.out.println("Cached End time : " + java.time.LocalDateTime.now());



                // Close resources
                if(cachedFileBufferedReader != null){
                    cachedFileBufferedReader.close();
                }
            }


            // Close Down Resources
            if(dataSender != null){
                dataSender.close();
            }

        } catch (IOException e) {
            System.out.println("Error Sending Cached file to client");
            e.printStackTrace();
        }
    }


    // send uncached data to client
    private void sendNotCachedToClient(String urlStr){

        try{

            // Compute a logical file name as per schema
            // This allows the files on stored on disk to resemble that of the URL it was taken from
            int fileExtensionIndex = urlStr.lastIndexOf(".");
            String fileExtension;

            // Get the type of file
            fileExtension = urlStr.substring(fileExtensionIndex, urlStr.length());

            // Get the initial file name
            String fileName = urlStr.substring(0,fileExtensionIndex);


            // Trim off http://www. as no need for it in file name
            fileName = fileName.substring(fileName.indexOf('.')+1);

            // Remove any illegal characters from file name
            fileName = fileName.replace("/", "__");
            fileName = fileName.replace('.','_');

            // Trailing / result in index.html of that directory being fetched
            if(fileExtension.contains("/")){
                fileExtension = fileExtension.replace("/", "__");
                fileExtension = fileExtension.replace('.','_');
                fileExtension += ".html";
            }

            fileName = fileName + fileExtension;



            // Attempt to create File to cache to
            boolean caching = true;
            File fileToCache = null;
            BufferedWriter fileToCacheBW = null;

            try{
                // Create File to cache
                System.out.println(fileName);
                fileToCache = new File(fileName);

                // print file name
                System.out.println(fileToCache.getName());

                if(!fileToCache.exists()){
                    fileToCache.createNewFile();
                }

                // Create Buffered output stream to write to cached copy of file
                fileToCacheBW = new BufferedWriter(new FileWriter(fileToCache));
            }
            catch (IOException e){
                System.out.println("Couldn't cache: " + fileName);
                caching = false;
                e.printStackTrace();
            } catch (NullPointerException e) {
                System.out.println("NPE opening file");
            }





            // Check if file is an image
            if((fileExtension.contains(".png")) || fileExtension.contains(".jpg") ||
                    fileExtension.contains(".jpeg")){
                // Create the URL
                URL remoteURL = new URL(urlStr);
                BufferedImage image = ImageIO.read(remoteURL);

                if(image != null) {
                    // Cache the image to disk
                    ImageIO.write(image, fileExtension.substring(1), fileToCache);

                    // Send response code to client
                    String line = "HTTP/1.0 200 OK\n" +
                            "Proxy-agent: ProxyServer/1.0\n" +
                            "\r\n";
                    dataSender.write(line);
                    dataSender.flush();

                    // Send them the image data
                    ImageIO.write(image, fileExtension.substring(1), clientSocket.getOutputStream());

                    // No image received from remote server
                } else {
                    System.out.println("Sending 404 to client as image wasn't received from server"
                            + fileName);
                    String error = "HTTP/1.0 404 NOT FOUND\n" +
                            "Proxy-agent: ProxyServer/1.0\n" +
                            "\r\n";
                    dataSender.write(error);
                    dataSender.flush();
                    return;
                }
            }

            // file without images
            else {

                // Create the URL
                URL remoteURL = new URL(urlStr);
                // Create a connection to remote server
                HttpURLConnection proxyToServerCon = (HttpURLConnection)remoteURL.openConnection();
                proxyToServerCon.setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded");
                proxyToServerCon.setRequestProperty("Content-Language", "en-US");
                proxyToServerCon.setUseCaches(false);
                proxyToServerCon.setDoOutput(true);

                // Create Buffered Reader from remote Server
                BufferedReader proxyToServerBR = new BufferedReader(new InputStreamReader(proxyToServerCon.getInputStream()));


                // Send success code to client
                String line = "HTTP/1.0 200 OK\n" +
                        "Proxy-agent: ProxyServer/1.0\n" +
                        "\r\n";
                dataSender.write(line);


                // Read from input stream between proxy and remote server
                while((line = proxyToServerBR.readLine()) != null){
                    // Send on data to client
                    dataSender.write(line);

                    // Write to our cached copy of the file
                    if(caching){
                        fileToCacheBW.write(line);
                    }
                }


                dataSender.flush();
                System.out.println("File not cached End time : " + java.time.LocalDateTime.now());

                // Close Down Resources
                if(proxyToServerBR != null){
                    proxyToServerBR.close();
                }
            }


            if(caching){
                // Ensure data written and add to our cached hash maps
                fileToCacheBW.flush();
                Proxy.updateMap(urlStr, fileToCache);
            }

            // Close down resources
            if(fileToCacheBW != null){
                fileToCacheBW.close();
            }

            if(dataSender != null){
                dataSender.close();
            }
        }

        catch (Exception e){
            e.printStackTrace();
        }
    }



    private void handleHTTPSRequest(String urlString){
        // Extract the URL and port of remote
        String url = urlString.substring(7);
        String tempArr[] = url.split(":");
        url = tempArr[0];
        // convert from string to integer
        int port  = Integer.parseInt(tempArr[1]);

        try{
            // get rid of the rest data on the stream
            for(int i=0;i<5;i++){
                dataReader.readLine();
            }

            // Get actual IP associated with this URL through DNS
            InetAddress address = InetAddress.getByName(url);

            // Open a socket to the remote server
            Socket proxyToServerSocket = new Socket(address, port);
            proxyToServerSocket.setSoTimeout(5000);

            // Send Connection established to the client
            String line = "HTTP/1.0 200 Connection established\r\n" +
                    "Proxy-Agent: ProxyServer/1.0\r\n" +
                    "\r\n";
            dataSender.write(line);
            dataSender.flush();



            // Client and Remote will both start sending data to proxy at this point
            // Proxy needs to asynchronously read data from each party and send it to the other party


            //Create a Buffered Writer betwen proxy and remote
            BufferedWriter proxyToServerBW = new BufferedWriter(new OutputStreamWriter(proxyToServerSocket.getOutputStream()));

            // Create Buffered Reader from proxy and remote
            BufferedReader proxyToServerBR = new BufferedReader(new InputStreamReader(proxyToServerSocket.getInputStream()));



            // Create a new thread to listen to client and transmit to server
            ClientToServerHttpsTransmit clientToServerHttps =
                    new ClientToServerHttpsTransmit(clientSocket.getInputStream(), proxyToServerSocket.getOutputStream());

            httpsClientToServer = new Thread(clientToServerHttps);
            httpsClientToServer.start();


            // Listen to remote server and relay to client
            try {
                final int byteSize = 4096;
                byte[] buffer = new byte[byteSize];
                int read;
                do {
                    read = proxyToServerSocket.getInputStream().read(buffer);
                    if (read > 0) {
                        clientSocket.getOutputStream().write(buffer, 0, read);
                        if (proxyToServerSocket.getInputStream().available() < 1) {
                            clientSocket.getOutputStream().flush();
                        }
                    }
                } while (read >= 0);
            }
            catch (SocketTimeoutException e) {
                System.out.println("Socket timeout");
            }
            catch (IOException e) {
                e.printStackTrace();
            }



            if(proxyToServerSocket != null){
                proxyToServerSocket.close();
            }

            if(proxyToServerBR != null){
                proxyToServerBR.close();
            }

            if(proxyToServerBW != null){
                proxyToServerBW.close();
            }

            if(dataSender != null){
                dataSender.close();
            }


        } catch (SocketTimeoutException e) {
            String line = "HTTP/1.0 504 Timeout Occured after 10s\n" +
                    "User-Agent: ProxyServer/1.0\n" +
                    "\r\n";
            try{
                dataSender.write(line);
                dataSender.flush();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        catch (Exception e){
            System.out.println("Error on HTTPS : " + urlString );
            e.printStackTrace();
        }
    }





    class ClientToServerHttpsTransmit implements Runnable{

        InputStream proxyToClientIS;
        OutputStream proxyToServerOS;


        public ClientToServerHttpsTransmit(InputStream proxyToClientIS, OutputStream proxyToServerOS) {
            this.proxyToClientIS = proxyToClientIS;
            this.proxyToServerOS = proxyToServerOS;
        }

        @Override
        public void run(){
            try {
                // Read byte by byte from client and send directly to server
                byte[] buffer = new byte[4096];
                int read;
                do {
                    read = proxyToClientIS.read(buffer);
                    if (read > 0) {
                        proxyToServerOS.write(buffer, 0, read);
                        if (proxyToClientIS.available() < 1) {
                            proxyToServerOS.flush();
                        }
                    }
                } while (read >= 0);
            }
            catch (SocketTimeoutException ste) {
                // TODO: handle exception
            }
            catch (IOException e) {
                System.out.println("Proxy to client HTTPS read timed out");
                e.printStackTrace();
            }
        }
    }



    private void blockedSiteRequested(){
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            String line = "HTTP/1.0 403 Access Forbidden \n" +
                    "User-Agent: ProxyServer/1.0\n" +
                    "\r\n";
            bufferedWriter.write(line);
            bufferedWriter.flush();
        } catch (IOException e) {
            System.out.println("Error writing to client when requested a blocked site");
            e.printStackTrace();
        }
    }
}
