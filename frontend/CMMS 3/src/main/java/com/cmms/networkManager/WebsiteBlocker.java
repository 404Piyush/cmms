package com.cmms.networkManager;

import com.cmms.utils.MongoDBHelper;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WebsiteBlocker {
    public static void blockWebsites() {
        List<String> blacklistedWebsites = getBlacklistedWebsites();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(getHostsFilePath(), true))) {
            for (String website : blacklistedWebsites) {
                writer.write("127.0.0.1 " + website);
                writer.newLine();
                writer.write("127.0.0.1 www." + website);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<String> getBlacklistedWebsites() {
        MongoCollection<Document> blacklistedWebsites = MongoDBHelper.getCollection("blacklisted_websites");
        List<String> websites = new ArrayList<>();
        for (Document doc : blacklistedWebsites.find()) {
            websites.add(doc.getString("website"));
        }
        return websites;
    }

    private static String getHostsFilePath() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return "C:\\Windows\\System32\\drivers\\etc\\hosts";
        } else {
            return "/etc/hosts";
        }
    }
}