package project.Utils;

import jade.lang.acl.ACLMessage;
import jade.core.AID;
import jade.core.Agent;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for logging agent conversations to files
 * Logs all ACL messages (sent and received) with full details
 * All logs for a single conversation/run are stored in a timestamped folder
 */
public class AgentLogger {
    private PrintWriter writer;
    private String agentName;
    private AID agentAID;  // Store agent AID for proper logging
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static final SimpleDateFormat folderDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    
    // Static variable to track the current log folder (shared across all agents in a run)
    private static String currentLogFolder = null;
    private static final Object folderLock = new Object();
    
    /**
     * Gets or creates the current timestamped log folder
     * All agents in the same run will use the same folder
     */
    private static String getCurrentLogFolder() {
        synchronized (folderLock) {
            if (currentLogFolder == null) {
                // Create new timestamped folder
                currentLogFolder = folderDateFormat.format(new Date());
            }
            return currentLogFolder;
        }
    }
    
    /**
     * Resets the log folder (useful for starting a new conversation/run)
     */
    public static void resetLogFolder() {
        synchronized (folderLock) {
            currentLogFolder = null;
        }
    }
    
    public AgentLogger(String agentName) {
        this.agentName = agentName;
        this.agentAID = null;
        try {
            // Get the current timestamped log folder
            String timestampFolder = getCurrentLogFolder();
            
            // Create log directory structure: logs/YYYY-MM-DD_HH-MM-SS/
            String logBaseDir = "logs";
            String logDir = logBaseDir + "/" + timestampFolder;
            java.io.File baseDir = new java.io.File(logBaseDir);
            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }
            java.io.File dir = new java.io.File(logDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String fileName = logDir + "/" + agentName + "_conversations.log";
            writer = new PrintWriter(new FileWriter(fileName, true)); // Append mode
            
            log("=== Agent Logger Initialized ===");
            log("Agent: " + agentName);
            log("Log folder: " + logDir);
            log("Log file: " + fileName);
            
        } catch (IOException e) {
            System.err.println("Failed to create log file for " + agentName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Sets the agent AID for proper logging of sent messages
     */
    public void setAgentAID(AID aid) {
        this.agentAID = aid;
        if (aid != null) {
            log("Agent AID set: " + aid.getName());
        }
    }
    
    /**
     * Sets the agent AID from an Agent object
     */
    public void setAgentAID(Agent agent) {
        if (agent != null) {
            setAgentAID(agent.getAID());
        }
    }
    
    /**
     * Logs a sent message
     */
    public void logSent(ACLMessage msg) {
        if (writer != null) {
            log(">>> SENT MESSAGE");
            logMessage(msg, true);
        }
    }
    
    /**
     * Logs a received message
     */
    public void logReceived(ACLMessage msg) {
        if (writer != null) {
            log("<<< RECEIVED MESSAGE");
            logMessage(msg, false);
        }
    }
    
    /**
     * Logs message details
     * @param msg The ACL message to log
     * @param isSent true if this is a sent message, false if received
     */
    private void logMessage(ACLMessage msg, boolean isSent) {
        if (writer == null) return;
        
        // Get all receivers
        List<String> receiverNames = new ArrayList<>();
        @SuppressWarnings("unchecked")
        Iterator<AID> receivers = msg.getAllReceiver();
        while (receivers.hasNext()) {
            AID receiver = receivers.next();
            receiverNames.add(receiver.getName());
        }
        
        // Determine sender and receiver based on message direction
        String fromName;
        String toName;
        
        if (isSent) {
            // For sent messages: From is this agent, To is the receiver(s)
            fromName = (agentAID != null ? agentAID.getName() : agentName);
            if (receiverNames.isEmpty()) {
                toName = "N/A (no receivers)";
            } else if (receiverNames.size() == 1) {
                toName = receiverNames.get(0);
            } else {
                toName = receiverNames.toString();  // Multiple receivers
            }
        } else {
            // For received messages: From is the sender, To is this agent
            fromName = (msg.getSender() != null ? msg.getSender().getName() : "N/A (unknown sender)");
            toName = (agentAID != null ? agentAID.getName() : agentName);
        }
        
        log("  From: " + fromName);
        if (isSent && receiverNames.size() > 1) {
            // Log each receiver separately for sent messages with multiple receivers
            log("  To: " + receiverNames.size() + " receivers");
            for (int i = 0; i < receiverNames.size(); i++) {
                log("    Receiver " + (i + 1) + ": " + receiverNames.get(i));
            }
        } else {
            log("  To: " + toName);
        }
        
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
     * Logs a conversation start (useful for tracking conversation flows)
     */
    public void logConversationStart(String conversationId, String description) {
        log("=== CONVERSATION START: " + conversationId + " ===");
        log("  Description: " + description);
    }
    
    /**
     * Logs a conversation end
     */
    public void logConversationEnd(String conversationId, String result) {
        log("=== CONVERSATION END: " + conversationId + " ===");
        log("  Result: " + result);
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

