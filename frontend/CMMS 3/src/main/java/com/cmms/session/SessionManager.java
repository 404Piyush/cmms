package com.cmms.session;

import com.cmms.utils.AdminChecker;
import com.mongodb.client.MongoCollection;
import com.cmms.utils.MongoDBHelper;
import org.bson.Document;
import java.util.Date;
import java.util.Random;

public class SessionManager {
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public static String createSession() {
        String sessionCode = generateSessionCode(6);
        MongoCollection<Document> sessions = MongoDBHelper.getCollection("sessions");

        sessions.insertOne(new Document("_id", sessionCode)
                .append("admin_pc", AdminChecker.getPCId())
                .append("created_at", new Date()));

        return sessionCode;
    }

    public static boolean validateSession(String sessionCode) {
        return MongoDBHelper.getCollection("sessions")
                .find(new Document("_id", sessionCode)).first() != null;
    }

    private static String generateSessionCode(int length) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }
}