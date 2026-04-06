import java.sql.*;

public class CheckDB {
    public static void main(String[] args) throws Exception {
        String[] dbs = {"dqms_OFFICER.db", "dqms_STUDENT_01.db"};
        
        for (String dbName : dbs) {
            System.out.println("Checking DB: " + dbName);
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);
                 Statement stmt = conn.createStatement()) {
                
                ResultSet rs = stmt.executeQuery("SELECT * FROM tickets");
                while (rs.next()) {
                    System.out.println(String.format("ID: %s | Reg: %s | Status: %s",
                            rs.getString("ticketId"),
                            rs.getString("registrationNumber"),
                            rs.getString("status")));
                }
            } catch (Exception e) {
                System.out.println("Failed to read " + dbName + ": " + e.getMessage());
            }
            System.out.println("-------------------------------------------------");
        }
    }
}
