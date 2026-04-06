package com.dqms.model;

import java.io.Serializable;

/**
 * Represents a student's ticket in the queue.
 */
public class Ticket implements Serializable, Comparable<Ticket> {

    private static final long serialVersionUID = 1L;

    private final String ticketId;
    private final String registrationNumber;
    private final String studentName;
    private final long issueTime;
    private final String originNodeId;
    private volatile String status;

    public Ticket(String registrationNumber, String studentName, long issueTime, String originNodeId) {
        this.registrationNumber = registrationNumber;
        this.studentName = studentName;
        this.issueTime = issueTime;
        this.originNodeId = originNodeId;
        this.ticketId = registrationNumber + "_" + issueTime;
        this.status = "WAITING";
    }

    @Override
    public int compareTo(Ticket other) {
        if (this.issueTime != other.issueTime) {
            return Long.compare(this.issueTime, other.issueTime);
        }
        return this.registrationNumber.compareTo(other.registrationNumber);
    }

    public String getTicketId()          { return ticketId; }
    public String getRegistrationNumber() { return registrationNumber; }
    public String getStudentName()       { return studentName; }
    public long   getIssueTime()         { return issueTime; }
    public String getOriginNodeId()      { return originNodeId; }
    public String getStatus()            { return status; }
    public void   setStatus(String s)    { this.status = s; }

    @Override
    public String toString() {
        return String.format("[%s | %s | %s | %s]", studentName, registrationNumber, originNodeId, status);
    }
}
