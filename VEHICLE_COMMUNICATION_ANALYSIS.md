# Vehicle Agent Communication Analysis

## Current Implementation

### Communication Type: **Ad-hoc Point-to-Point ACL Messaging**

The current implementation uses basic ACL (Agent Communication Language) messages without following formal FIPA interaction protocols.

---

## 1. State Query Communication

### Flow:
```
Delivery Agent → Vehicle Agent: "QUERY_STATE"
Vehicle Agent → Delivery Agent: "STATE:free|CAPACITY:20|NAME:Thanh"
```

### Code (VehicleAgent.java):
```java
if (content.equals("QUERY_STATE")) {
    ACLMessage reply = msg.createReply();
    reply.setPerformative(ACLMessage.INFORM);
    reply.setContent("STATE:" + state + "|CAPACITY:" + capacity + "|NAME:" + vehicleName);
    send(reply);
}
```

### Characteristics:
- **Pattern**: Request-Reply (Query-Inform)
- **Performatives**: No specific performative for request, `INFORM` for reply
- **Protocol**: None (ad-hoc)
- **Synchronous**: Delivery Agent waits for responses

---

## 2. Route Assignment Communication

### Flow:
```
Delivery Agent → Vehicle Agent: "ASSIGN_ROUTE:R1"
Vehicle Agent → Delivery Agent: "ROUTE_ACCEPTED:R1|VEHICLE:Thanh"
```

### Code (VehicleAgent.java):
```java
else if (content.startsWith("ASSIGN_ROUTE:")) {
    assignedRouteId = content.substring("ASSIGN_ROUTE:".length());
    
    ACLMessage reply = msg.createReply();
    reply.setPerformative(ACLMessage.CONFIRM);
    reply.setContent("ROUTE_ACCEPTED:" + assignedRouteId + "|VEHICLE:" + vehicleName);
    send(reply);
}
```

### Characteristics:
- **Pattern**: Command-Confirmation
- **Performatives**: Unspecified for request, `CONFIRM` for reply
- **Protocol**: None (ad-hoc)
- **Current Status**: **Simplified - Not fully implemented**

---

## Issues with Current Approach

❌ **Not using FIPA protocols** - Makes it harder to integrate with other agent systems
❌ **No negotiation** - Vehicle can't refuse or negotiate
❌ **No failure handling** - What if vehicle can't complete the route?
❌ **Simplified** - Route assignment is acknowledged but not actually executed

---

## Recommended FIPA Protocols

### Option 1: **FIPA Request Protocol** (Simplest)

Best for: **Task assignment where vehicle must comply**

```
Delivery Agent → Vehicle Agent: REQUEST (assign route)
Vehicle Agent → Delivery Agent: AGREE (I'll do it)
Vehicle Agent → Delivery Agent: INFORM (Done) or FAILURE (Can't do it)
```

**Performatives:**
- `REQUEST` - Request action
- `AGREE` / `REFUSE` - Accept or decline
- `INFORM` / `FAILURE` - Report result

**Use Case:** When vehicle must execute the assigned route (no negotiation)

---

### Option 2: **FIPA Contract Net Protocol** (Most Appropriate)

Best for: **Task allocation with negotiation**

```
Delivery Agent → All Vehicles: CFP (Call for Proposal) - "Who can serve route R1?"
Vehicle 1 → Delivery Agent: PROPOSE "I can do it for cost X"
Vehicle 2 → Delivery Agent: REFUSE "I'm absent"
Vehicle 3 → Delivery Agent: PROPOSE "I can do it for cost Y"

Delivery Agent → Vehicle 1: ACCEPT_PROPOSAL
Delivery Agent → Vehicle 3: REJECT_PROPOSAL

Vehicle 1 → Delivery Agent: INFORM "Route completed"
```

**Performatives:**
- `CFP` (Call for Proposal) - Request bids for task
- `PROPOSE` / `REFUSE` - Submit bid or decline
- `ACCEPT_PROPOSAL` / `REJECT_PROPOSAL` - Award or reject bid
- `INFORM` / `FAILURE` - Report completion

**Use Case:** When you want vehicles to compete/bid for routes based on:
- Current location
- Capacity
- Cost/efficiency
- Availability

---

### Option 3: **FIPA Query Protocol**

Best for: **Information retrieval only** (state queries)

```
Delivery Agent → Vehicle Agent: QUERY_IF (state query)
Vehicle Agent → Delivery Agent: INFORM (state info)
```

**Performatives:**
- `QUERY_IF` / `QUERY_REF` - Request information
- `INFORM` / `FAILURE` - Provide information

**Use Case:** Already appropriate for state queries (just need to add QUERY_IF performative)

---

## Recommended Implementation

### For State Queries: **FIPA Query Protocol**

**Delivery Agent sends:**
```java
ACLMessage query = new ACLMessage(ACLMessage.QUERY_IF);
query.addReceiver(vehicleAID);
query.setContent("QUERY_STATE");
query.setProtocol(FIPANames.InteractionProtocol.FIPA_QUERY);
send(query);
```

**Vehicle Agent responds:**
```java
if (msg.getPerformative() == ACLMessage.QUERY_IF && content.equals("QUERY_STATE")) {
    ACLMessage reply = msg.createReply();
    reply.setPerformative(ACLMessage.INFORM);
    reply.setContent("STATE:" + state + "|CAPACITY:" + capacity + "|NAME:" + vehicleName);
    send(reply);
}
```

---

### For Route Assignment: **FIPA Contract Net Protocol** (Recommended)

**Why Contract Net?**
✅ Allows vehicles to **bid** based on their state (free/absent)
✅ Vehicles can **refuse** if they're absent or at capacity
✅ Delivery Agent can **choose** the best vehicle for each route
✅ **Industry standard** for task allocation in multi-agent systems
✅ Built-in **failure handling** and **negotiation**

**Implementation Example:**

**Delivery Agent (Initiator):**
```java
import jade.proto.ContractNetInitiator;
import jade.domain.FIPANames;

// Create CFP for route assignment
ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
for (AID vehicleAID : freeVehicles) {
    cfp.addReceiver(vehicleAID);
}
cfp.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
cfp.setContent("CFP:ROUTE_R1:{customer_data}");
cfp.setReplyByDate(new Date(System.currentTimeMillis() + 5000)); // 5 second deadline

addBehaviour(new ContractNetInitiator(this, cfp) {
    protected void handleAllResponses(Vector responses, Vector acceptances) {
        // Choose best proposal
        ACLMessage bestProposal = null;
        double bestCost = Double.MAX_VALUE;
        
        for (ACLMessage response : responses) {
            if (response.getPerformative() == ACLMessage.PROPOSE) {
                double cost = parseProposalCost(response.getContent());
                if (cost < bestCost) {
                    bestCost = cost;
                    bestProposal = response;
                }
            }
        }
        
        // Accept best proposal, reject others
        for (ACLMessage response : responses) {
            ACLMessage reply = response.createReply();
            if (response == bestProposal) {
                reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
            } else {
                reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
            }
            acceptances.add(reply);
        }
    }
    
    protected void handleInform(ACLMessage inform) {
        System.out.println("Route completed by: " + inform.getSender().getLocalName());
    }
});
```

**Vehicle Agent (Responder):**
```java
import jade.proto.ContractNetResponder;
import jade.domain.FIPANames;

// Add Contract Net responder behavior
addBehaviour(new ContractNetResponder(this, MessageTemplate.MatchProtocol(
    FIPANames.InteractionProtocol.FIPA_CONTRACT_NET)) {
    
    protected ACLMessage handleCfp(ACLMessage cfp) {
        ACLMessage reply = cfp.createReply();
        
        if (state.equals("free")) {
            // Calculate proposal (e.g., estimated cost)
            double estimatedCost = calculateRouteCost(cfp.getContent());
            reply.setPerformative(ACLMessage.PROPOSE);
            reply.setContent("COST:" + estimatedCost);
        } else {
            // Refuse if absent
            reply.setPerformative(ACLMessage.REFUSE);
            reply.setContent("VEHICLE_ABSENT");
        }
        return reply;
    }
    
    protected ACLMessage handleAcceptProposal(ACLMessage cfp, ACLMessage propose, ACLMessage accept) {
        ACLMessage inform = accept.createReply();
        inform.setPerformative(ACLMessage.INFORM);
        
        // Execute the route (simplified)
        assignedRouteId = parseRouteId(cfp.getContent());
        
        inform.setContent("ROUTE_COMPLETED:" + assignedRouteId);
        return inform;
    }
});
```

---

## Comparison Table

| Aspect | Current (Ad-hoc) | FIPA Request | FIPA Contract Net |
|--------|------------------|--------------|-------------------|
| **Negotiation** | ❌ No | ❌ No | ✅ Yes |
| **Vehicle can refuse** | ❌ No | ✅ Yes | ✅ Yes |
| **Best vehicle selection** | Manual | Manual | ✅ Automatic |
| **Failure handling** | ❌ No | ✅ Yes | ✅ Yes |
| **FIPA compliant** | ❌ No | ✅ Yes | ✅ Yes |
| **Industry standard** | ❌ No | ⚠️ Basic | ✅ Yes |
| **Complexity** | Low | Medium | High |
| **Best for** | Simple demos | Commands | Task allocation |

---

## Summary

### Current State:
🔴 **Ad-hoc point-to-point messaging** - Simple but not scalable

### Recommendation:
🟢 **Use FIPA Contract Net Protocol for route assignment**
- Industry standard for task allocation
- Vehicles can negotiate and refuse
- Automatic selection of best vehicle
- Built-in failure handling

🟢 **Use FIPA Query Protocol for state queries**
- Just add `QUERY_IF` performative to current implementation

---

## Benefits of Upgrading to FIPA Protocols

1. **Standardization** - Other agents can understand your system
2. **Robustness** - Built-in error handling and timeouts
3. **Scalability** - Protocols handle complex negotiations
4. **Flexibility** - Vehicles can negotiate based on their capabilities
5. **Professional** - Industry-standard approach for multi-agent systems

---

## Next Steps (If You Want to Implement)

1. **Phase 1**: Add `QUERY_IF` performative to state queries ✅ Easy
2. **Phase 2**: Implement FIPA Contract Net for route assignment 🔨 Medium
3. **Phase 3**: Add route execution and completion reporting 🚀 Advanced

Would you like me to implement the FIPA Contract Net Protocol for proper route assignment?


