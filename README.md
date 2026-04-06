# Decentralized Queue Management System (DQMS)
## Chuka University | Computer Science Project (Y3S2)

A robust, decentralized queue management system built with JavaFX and SQLite. This system allows for real-time synchronization of student queue positions across multiple computer nodes using UDP multicast for discovery and TCP for state replication.

---

##  Key Features

- **Decentralized Architecture:** No central server is required. Nodes discover each other automatically on the local network.
- **Role-Based Access Control:**
    - **System Admin:** Full visibility of the global queue, authority to clear students, and ability to reset the entire network queue.
    - **Student Portal:** Dynamic entry of registration details and real-time tracking of queue position and status.
- **Fault Tolerance:** If a node goes offline, the rest of the network continues to function. Data is persisted locally in SQLite.
- **Real-time Sync:** Uses custom TCP protocols to ensure that when an Admin clears a student, the update is reflected instantly on all student nodes.

---

## 🛠 Prerequisites

| Requirement | Version | Link |
| :--- | :--- | :--- |
| **Java JDK** | 17 or higher | [Download](https://adoptium.net) |
| **Apache Maven** | 3.8 or higher | [Download](https://maven.apache.org/download) |

---

## 🚀 How to Run the Application

To demonstrate the decentralized nature, you should run at least two instances (one Admin and one Student) in separate terminal windows.

### 1. Build the Project
Open your terminal in the project root and run:
```bash
mvn clean compile
```

### 2. Start the Admin Node
The Admin node acts as the supervisor for the queue.
```bash
mvn javafx:run -Djavafx.args="5001 ADMIN_01 ADMIN"
```

### 3. Start a Student Node
Open a **new terminal window** and run:
```bash
mvn javafx:run -Djavafx.args="5002 STU_NODE"
```
*Note: You can start multiple student nodes by using different ports (e.g., 5003, 5004).*

---

## 📖 Usage Instructions

1. **Launch the Admin Node:** Navigate to the "Queue" tab to see the live table.
2. **Launch a Student Node:** 
    - Enter a **Registration Number** and **Full Name**.
    - Click **"Request Queue Position"**.
3. **Observation:** Switch back to the Admin Node. The student's details will appear instantly in the table.
4. **Action:** On the Admin Node, select the student and click **"Mark as Cleared"**.
5. **Verification:** The Student Node will instantly update to show a **"CLEARED"** status and an "OK" position.
6. **Reset:** Use the **"Reset Global Queue"** button on the Admin Dashboard to wipe the history across all connected nodes for a fresh test.

---

## 📂 Project Structure

```text
src/main/java/com/dqms/
├── app/
│   └── Main.java                # Application entry and service coordination
├── db/
│   └── DatabaseManager.java     # SQLite persistence logic
├── model/
│   ├── Ticket.java              # Core queue entity
│   ├── Message.java             # Network communication protocol
│   └── NodeInfo.java            # Peer registry data
├── network/
│   ├── TCPServer/Client.java    # Peer-to-peer data transfer
│   └── UDPDiscoveryService.java # Automatic node discovery
├── queue/
│   └── QueueManager.java        # Central business logic and sync handling
└── ui/
    └── MainController.java      # JavaFX UI event handling
```

---

## 📝 Important Notes
- **Persistence:** Each node creates its own `.db` file (e.g., `dqms_ADMIN_01.db`). These files are ignored by Git to ensure every new machine starts with a clean slate.
- **Networking:** Ensure your firewall allows UDP traffic on port `4446` and TCP traffic on the ports you specify (default `5001+`).
