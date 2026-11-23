package com.sumasoft.tracking.imports;

import java.util.Locale;

/**
 * The columns of the monthly ticket sheets, and how their headings are recognised.
 * Different projects name the same column differently, for example
 *   Codility (Zendesk):    "Ticket ID", "Assignee", "Topic"
 *   SouthCo  (Keypanion):  "Case No",   "Case Owner", "Scenario", "Issue Description"
 * A new project with other headings only needs its names added to recognise() below.
 * Headings are compared without capital letters, spaces or punctuation, so
 * "Escalated ( Yes / No )", "escalated" and "ESCALATED" all mean the same column.
 */
public final class ImportColumns {

    public static final String SR_NO = "Sr.no";
    public static final String TICKET_ID = "Ticket ID";
    public static final String CREATED_DATE = "Date of Creation";
    public static final String TIMEFRAME = "Timeframe";
    public static final String CREATED_TIME = "Time of Creation";
    public static final String ASSIGNEE = "Assignee";
    public static final String DAY = "Day";
    public static final String ESCALATED = "Escalated";
    public static final String CLOSED_DATE = "Date of Closure";
    public static final String CLOSED_TIME = "Time of Closure";
    public static final String RESOLUTION_TIME = "Resolution Time";
    public static final String CLOSED_BY = "Closed By";
    public static final String STATUS = "Status";
    public static final String SATISFACTION = "Satisfaction Rating";
    public static final String PRIORITY = "Priority";
    public static final String SOURCE = "Source";
    public static final String ISSUE_TYPE = "Issue Type";
    public static final String PLAN = "Plan";
    public static final String TOPIC = "Topic";
    public static final String CATEGORY = "Category";
    public static final String SUBCATEGORY = "Subcategory";
    public static final String REQUESTER = "Requester";
    public static final String REQUESTER_ROLE = "Requester Role";
    public static final String DESCRIPTION = "Description";

    /** Columns without which the file cannot be imported. */
    public static final String[] REQUIRED = {TICKET_ID, CREATED_DATE, STATUS};

    private ImportColumns() {
    }

    /** Heading text -> one of the column names above, or null when the heading is not known. */
    public static String recognise(String heading) {
        if (heading == null) {
            return null;
        }
        String h = heading.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (h.isEmpty()) {
            return null;
        }
        if (h.startsWith("escalated")) {
            return ESCALATED;
        }
        if (h.startsWith("resolutiontime")) {
            return RESOLUTION_TIME;
        }
        return switch (h) {
            case "srno", "sno", "srnumber", "serialno", "serialnumber", "sr" -> SR_NO;
            case "ticketid", "ticketno", "ticketnumber", "caseno", "casenumber", "caseid" -> TICKET_ID;
            case "dateofcreation", "createddate", "creationdate" -> CREATED_DATE;
            case "timeframe" -> TIMEFRAME;
            case "timeofcreation", "createdtime", "creationtime" -> CREATED_TIME;
            case "assignee", "assignedto", "caseowner", "owner" -> ASSIGNEE;
            case "day" -> DAY;
            case "dateofclosure", "closeddate", "closuredate" -> CLOSED_DATE;
            case "timeofclosure", "closedtime", "closuretime" -> CLOSED_TIME;
            case "closedby" -> CLOSED_BY;
            case "status" -> STATUS;
            case "satisfactionrating", "satisfaction", "csat" -> SATISFACTION;
            case "priority" -> PRIORITY;
            case "source", "channel" -> SOURCE;
            case "issuetype" -> ISSUE_TYPE;
            case "plan" -> PLAN;
            case "topic", "scenario" -> TOPIC;
            case "category" -> CATEGORY;
            case "subcategory" -> SUBCATEGORY;
            case "requester" -> REQUESTER;
            case "requesterrole" -> REQUESTER_ROLE;
            case "issuedescription", "description" -> DESCRIPTION;
            default -> null;
        };
    }
}