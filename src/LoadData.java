import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class LoadData {

    public static void readBlockedSites() {
        try {
            File myObj = new File("blockedSites.txt");
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                System.out.println(data);
                Proxy.blockedSites.add(data);
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

    }

    public static void readCachedSites() {
        try {
            File myObj = new File("cachedSites.txt");
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                System.out.println(data);
                String tempStr = data;
                tempStr = tempStr.replace("/",":");
                tempStr = tempStr.replace("__","/");
                File temp = new File("data");
                Proxy.lruCache.put(tempStr, temp);
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

}
