# 🚚 COS30018 – Vehicle Routing Problem Project Workflow (Full Plan)

**Duration:** 6 Weeks (≈ 1.5 Months)
**Team Size:** 2 Members (Optimization Lead & System/GUI Engineer)
**Due Date:** 27 October 2024

---

## 📘 Project Summary

You will implement and demonstrate a **Vehicle Routing Problem (VRP)** solution for a courier company using **multi-agent architecture** and **search/optimization algorithms**.

---

## 🚫 Prohibited Elements

- ❌ You **cannot rely solely on existing solvers** such as Google OR-Tools or OptaPlanner.You **must implement your own optimization/search algorithm** (e.g., GA, ACO, PSO, or CSP).
- ❌ Poor coding practice (unstructured code or no comments) → **Up to -20 marks penalty**.
- ❌ Not showing weekly progress → **Up to -50 marks penalty**.
- ❌ Late submission: **-10% per day**, **0% after 5 days**.

---

## 🏆 Requirements for D/HD (Distinction/High Distinction)

To achieve a D/HD, the project must demonstrate:

1. ✅ **Core Functionality**

   - Complete **Tasks 1–3** (interaction, optimization, and constraints).
   - Proper **communication protocol** between MRA and DAs with a sequence diagram.
2. ✅ **Optimization Logic**

   - Use an implemented optimization algorithm to generate **optimal route assignments** under capacity and distance constraints.
3. ✅ **GUI and Visualization**

   - Functional GUI showing routes, costs, and delivery visualization.
4. ✅ **Research Extension (20%)**

   - Implement **Extension Option 1:** VRP with Time Windows, or
   - Implement **Extension Option 2:** Bidding System (Amazon-Uber style).
5. ✅ **Documentation & Presentation**

   - 8–10 page project report (architecture, algorithms, analysis).
   - 6–8 minute presentation video.
   - Demonstrated weekly progress and teamwork evidence.

---

## 💡 Guidance

- You may reference **Google OR-Tools** or **OptaPlanner** as baseline tests, but not as your main implementation.
- Use clear **sequence diagrams** for inter-agent communication.
- Demonstrate strong **team collaboration** and **version control** through Git (GitHub/GitLab/Bitbucket).
- Follow good software engineering practices (clear architecture, modular design, meaningful comments).

---

## 🧭 Full Project Workflow (with Requirements)

### **Phase 1 – Understanding & Foundation (Week 1–2)**

| Task                       | Description                                           | Responsible | Requirements                                                                     | Deadline      |
| -------------------------- | ----------------------------------------------------- | ----------- | -------------------------------------------------------------------------------- | ------------- |
| Study ChocoSolver          | Understand modeling, constraints, and search methods. | Member A    | Use ChocoSolver to implement at least one custom optimization component.         | End of Week 1 |
| Implement CVRP Constraints | Build basic CVRP (capacity + route) model.            | Member A    | Must correctly prioritize*number of items delivered* over *travel distance*. | Mid Week 2    |
| Validate CVRP Model        | Test CVRP with random/small datasets.                 | Member A    | Demonstrate correctness and feasibility.                                         | End of Week 2 |
| Setup Repo & Draft GUI     | Prepare Git repo structure and GUI wireframe.         | Member B    | Must provide read-only repo access within 1 week.                                | End of Week 2 |

---

### **Phase 2 – Multi-Agent System Implementation (Week 3–4)**

| Task                          | Description                                  | Responsible | Requirements                                             | Deadline     |
| ----------------------------- | -------------------------------------------- | ----------- | -------------------------------------------------------- | ------------ |
| Design Agent Architecture     | Define MRA, DAs, and User roles.             | Member A    | Include architecture diagram in report.                  | Start Week 3 |
| Define Communication Format   | Design message structure (JSON/XML).         | Member B    | Must match report sequence diagram.                      | Start Week 3 |
| Implement Agent Communication | Code message passing and route allocation.   | Member A    | Must follow defined protocol (Task 1 of marking scheme). | Mid Week 3   |
| Build Mock GUI Interaction    | Prototype GUI data exchange.                 | Member B    | GUI should simulate receiving agent updates.             | Mid Week 3   |
| Integrate CVRP into MRA       | Combine optimization with multi-agent logic. | Member A    | Must satisfy Basic Requirement 2 (max distance).         | End Week 3   |
| Extend Society Features       | Add bidding system or VRPTW variant.         | Both        | Must meet Research Component (+20 marks).                | End Week 4   |

---

### **Phase 3 – GUI Development (Week 5–6)**

| Task                         | Description                                              | Responsible | Requirements                                             | Deadline   |
| ---------------------------- | -------------------------------------------------------- | ----------- | -------------------------------------------------------- | ---------- |
| Prototype GUI with D3.js     | Visualize routes and movements as a tree or path.        | Member B    | Must show cost per route (Task 4).                       | Mid Week 5 |
| Add User Interaction         | Add input fields for vehicles/items, parameter settings. | Member B    | GUI must link to backend data and allow user prediction. | End Week 5 |
| Optimize & Extend Algorithms | Tune CVRP performance, finalize backend stability.       | Member A    | Demonstrate measurable improvements.                     | End Week 5 |
| UI Polishing & Enhancement   | Refine interface, visuals, layout.                       | Member B    | Must meet usability and clarity standards.               | Mid Week 6 |
| System Integration & Testing | Merge GUI + multi-agent + backend and test.              | Both        | Must run full end-to-end scenario.                       | End Week 6 |

---

### **Phase 4 – Finalization & Documentation (Final Week)**

| Task                       | Description                                                    | Responsible | Requirements                                        | Deadline         |
| -------------------------- | -------------------------------------------------------------- | ----------- | --------------------------------------------------- | ---------------- |
| Code Refinement            | Optimize code readability and structure.                       | Both        | Must remove redundancy and ensure modular design.   | Early Final Week |
| Write Detailed Report      | Write a full report covering algorithms, GUI, and experiments. | Both        | 8–10 pages; include visuals and diagrams.          | Mid Final Week   |
| Prepare Demo & Video       | Record complete system demo and explanation.                   | Member B    | 6–8 min video demonstrating workflow.              | Mid Final Week   |
| Final Testing & Submission | Ensure all components work together smoothly.                  | Both        | Submit full package (.zip + video) before deadline. | End Final Week   |

---

## ⚙️ Team Responsibilities Overview

| Role                                        | Key Focus                          | Tasks                                                                         |
| ------------------------------------------- | ---------------------------------- | ----------------------------------------------------------------------------- |
| **Member A – Optimization Lead**     | Algorithm & Multi-Agent Logic      | ChocoSolver, CVRP, route optimization, agent communication, bidding extension |
| **Member B – System & GUI Engineer** | System Integration & Visualization | GUI (D3.js), user interaction, data exchange, report visuals, demo video      |

---

## 🗓️ Weekly Milestones

| Week                 | Focus                    | Key Deliverables            |
| -------------------- | ------------------------ | --------------------------- |
| **Week 1**     | ChocoSolver learning     | Test code for constraints   |
| **Week 2**     | Implement & test CVRP    | Working CVRP + repo setup   |
| **Week 3**     | Multi-agent setup        | Communication + data schema |
| **Week 4**     | Extensions & integration | Bidding or VRPTW prototype  |
| **Week 5**     | GUI development          | Interactive visualization   |
| **Week 6**     | Integration & testing    | Full end-to-end run         |
| **Final Week** | Report, demo, refinement | Polished submission + video |

---

## ✅ Final Deliverables Checklist

- [ ] Working **multi-agent VRP system**
- [ ] Implemented **custom optimization algorithm**
- [ ] **Sequence diagram** for agent communication
- [ ] **GUI with visualization + user input**
- [ ] **Research extension** (bidding or VRPTW)
- [ ] **Clean, modular, well-documented code**
- [ ] **Detailed project report (8–10 pages)**
- [ ] **Demo video (6–8 minutes)**
- [ ] **Final integrated submission (.zip + video)**

---

_Last updated: 05 October 2025_
