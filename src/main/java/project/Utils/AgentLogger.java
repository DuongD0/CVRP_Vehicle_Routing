package project.Utils;

import jade.lang.acl.ACLMessage;
import jade.core.AID;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

/**
 * Utility class for logging agent conversations to files
 */
public class AgentLogger {
    private PrintWriter writer;
    private String agentName;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    public AgentLogger(String agentName) {
        this.agentName = agentName;
        try {
            // Create log file in logs directory
            String logDir = "logs";
            java.io.File dir = new java.io.File(logDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String fileName = logDir + "/" + agentName + "_conversations.log";
            writer = new PrintWriter(new FileWriter(fileName, true)); // Append mode
            
            log("=== Agent Logger Initialized ===");
            log("Agent: " + agentName);
            log("Log file: " + fileName);
            
        } catch (IOException e) {
            System.err.println("Failed to create log file for " + agentName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Logs a sent message
     */
    public void logSent(ACLMessage msg) {
        if (writer != null) {
            log(">>> SENT MESSAGE");
            logMessage(msg, "TO");
        }
    }
    
    /**
     * Logs a received message
     */
    public void logReceived(ACLMessage msg) {
        if (writer != null) {
            log("<<< RECEIVED MESSAGE");
            logMessage(msg, "FROM");
        }
    }
    
    /**
     * Logs message details
     */
    private void logMessage(ACLMessage msg, String direction) {
        if (writer == null) return;
        
        log("  Direction: " + direction);
        
        // Get receivers
        @SuppressWarnings("unchecked")
        Iterator<AID> receivers = msg.getAllReceiver();
        if (receivers.hasNext()) {
            AID receiver = receivers.next();
            log("  To: " + receiver.getName());
            // Log additional receivers if any
            while (receivers.hasNext()) {
                log("  To (additional): " + receivers.next().getName());
            }
        } else {
            log("  To: N/A");
        }
        
        log("  From: " + (msg.getSender() != null ? msg.getSender().getName() : "N/A"));
        log("  Performative: " + ACLMessage.getPerformative(msg.getPerformative()));
        log("  Protocol: " + (msg.getProtocol() != null ? msg.getProtocol() : "N/A"));
        log("  Conversation ID: " + (msg.getConversationId() != null ? msg.getConversationId() : "N/A"));
        log("  Reply With: " + (msg.getReplyWith() != null ? msg.getReplyWith() : "N/A"));
        log("  In Reply To: " + (msg.getInReplyTo() != null ? msg.getInReplyTo() : "N/A"));
        log("  Content: " + (msg.getContent() != null ? msg.getContent() : "(empty)"));
        log("  Language: " + (msg.getLanguage() != null ? msg.getLanguage() : "N/A"));
        log("  Ontology: " + (msg.getOntology() != null ? msg.getOntology() : "N/A"));
        log("---");
    }
    
    /**
     * Logs a general message
     */
    public void log(String message) {
        if (writer != null) {
            String timestamp = dateFormat.format(new Date());
            writer.println("[" + timestamp + "] " + message);
            writer.flush();
        }
    }
    
    /**
     * Logs an event (state change, etc.)
     */
    public void logEvent(String event) {
        log("*** EVENT: " + event);
    }
    
    /**
     * Closes the log file
     */
    public void close() {
        if (writer != null) {
            log("=== Agent Logger Closed ===");
            writer.close();
        }
    }
}

