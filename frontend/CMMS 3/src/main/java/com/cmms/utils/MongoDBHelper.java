package com.cmms.utils;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public class MongoDBHelper {
    private static final String CONN_STR = "mongodb+srv://piyushutkar:IzjwOgkkXCjwmCWA@cmmscluster.ko4f8.mongodb.net/";
    private static final MongoClient mongoClient = MongoClients.create(CONN_STR);
    private static final MongoDatabase database = mongoClient.getDatabase("classroom");

    public static MongoCollection<Document> getCollection(String name) {
        return database.getCollection(name);
    }
}