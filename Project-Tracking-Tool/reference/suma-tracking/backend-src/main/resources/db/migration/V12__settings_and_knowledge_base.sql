-- =====================================================================
-- V12 (Step 38): Settings and Knowledge Base.
--   system_settings      tool-wide settings (Settings page, Super Admin)
--   kb_articles          help guides: how the tool works, in simple English
--   kb_article_versions  every saved version of every guide (change history)
--   kb_unanswered        searches and questions that found no guide, so a guide can be added
-- The Knowledge Base only explains the tool. It never opens or changes tickets.
-- Never edit this file after it has run.
-- =====================================================================

CREATE TABLE system_settings (
    setting_key         VARCHAR(80) PRIMARY KEY,
    setting_value       VARCHAR(500) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_user_id  BIGINT REFERENCES users (id)
);

INSERT INTO system_settings (setting_key, setting_value) VALUES
 ('company.name', 'Suma Soft'),
 ('project.defaultTimeZone', 'Asia/Kolkata'),
 ('sla.default.CRITICAL.responseMinutes', '60'),
 ('sla.default.CRITICAL.resolutionMinutes', '240'),
 ('sla.default.HIGH.responseMinutes', '120'),
 ('sla.default.HIGH.resolutionMinutes', '480'),
 ('sla.default.MEDIUM.responseMinutes', '240'),
 ('sla.default.MEDIUM.resolutionMinutes', '1440'),
 ('sla.default.LOW.responseMinutes', '480'),
 ('sla.default.LOW.resolutionMinutes', '2880'),
 ('automation.alertLookbackDays', '30'),
 ('automation.maxNotificationsPerRun', '300'),
 ('automation.dailySummaryHour', '9');

CREATE TABLE kb_articles (
    id                  BIGSERIAL PRIMARY KEY,
    slug                VARCHAR(120) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    category            VARCHAR(60) NOT NULL,
    title               VARCHAR(200) NOT NULL,
    summary             VARCHAR(500) NOT NULL,
    body                TEXT NOT NULL,
    keywords            VARCHAR(1000) NOT NULL DEFAULT '',
    sort_order          INTEGER NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('PUBLISHED', 'HIDDEN')),
    view_count          INTEGER NOT NULL DEFAULT 0,
    helpful_yes         INTEGER NOT NULL DEFAULT 0,
    helpful_no          INTEGER NOT NULL DEFAULT 0,
    version_no          INTEGER NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by_user_id  BIGINT REFERENCES users (id)
);

CREATE TABLE kb_article_versions (
    id                  BIGSERIAL PRIMARY KEY,
    article_id          BIGINT NOT NULL REFERENCES kb_articles (id),
    version_no          INTEGER NOT NULL,
    category            VARCHAR(60) NOT NULL,
    title               VARCHAR(200) NOT NULL,
    summary             VARCHAR(500) NOT NULL,
    body                TEXT NOT NULL,
    keywords            VARCHAR(1000) NOT NULL,
    status              VARCHAR(20) NOT NULL,
    changed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by_user_id  BIGINT REFERENCES users (id),
    CONSTRAINT kb_article_versions_unique UNIQUE (article_id, version_no)
);

CREATE TABLE kb_unanswered (
    id                BIGSERIAL PRIMARY KEY,
    question          VARCHAR(300) NOT NULL,
    mode              VARCHAR(10) NOT NULL CHECK (mode IN ('SEARCH', 'ASK')),
    asked_by_user_id  BIGINT REFERENCES users (id),
    asked_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_kb_unanswered_time ON kb_unanswered (asked_at DESC);

-- The starting guides (43). The Super Admin can edit them, hide them and add new ones.
INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$what-is-suma-tracking$kb$, $kb$Getting started$kb$, $kb$What is Suma Tracking?$kb$, $kb$One place where Suma Soft tracks support tickets from every client and every tool.$kb$, $kb$about tool introduction overview purpose what is this software app platform zendesk keypanion codility southco$kb$, 10,
$kb$## In one line
Suma Tracking is our own website where all support tickets of all clients are kept in one place.

## Why we need it
Every client uses a different ticket tool. Codility uses Zendesk. SouthCo uses Keypanion. Tomorrow a new client may use some other tool. Earlier, every team kept its own Excel sheet, so nobody could see the full picture.

Suma Tracking brings all these tickets together. Now managers and agents can see, in one screen:
- How many tickets came today, this week or this month
- Which tickets are still open and who is working on them
- Which tickets are late (SLA breached)
- How each employee and each project is doing

## Where the tickets come from
Right now the agents fill a daily Excel sheet (one sheet per month, like Nov26 or Dec26). That sheet is uploaded in Integration Management. The tool reads it and creates or updates the tickets. The same sheet can be uploaded again and again. It never makes double tickets.

Later, the tool will also connect directly to Zendesk and Keypanion.

## What you can do here
- Dashboard: see numbers and charts
- Ticket Management: find any ticket and work on it
- Team Management: add employees and give them projects
- SLA Management: see which tickets are late
- Notifications: get alerts
- Knowledge Base: this help section

Tip: If you are new, read "Sign in for the first time" and "What each menu item does" next.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$first-sign-in$kb$, $kb$Getting started$kb$, $kb$Sign in for the first time$kb$, $kb$How to open the tool, sign in with your email and choose your own password.$kb$, $kb$login log in signin sign in first time new user start open website password temporary credentials email username kaise login kare$kb$, 20,
$kb$## Before you start
Your administrator (Super Admin) creates your account. They will give you:
- Your login email (your company email, for example name@sumasoft.com)
- A temporary password

## Steps
1. Open the tool in Chrome. On your own computer the address is usually http://localhost:5173
2. Type your company email in the Email box.
3. Type the temporary password in the Password box.
4. Press Sign in.
5. The tool now asks you to choose your own password. Type the temporary password once, then your new password two times.
6. Press the button to save. You are now inside the tool.

## Rules for the new password
- At least 10 characters
- At least one letter and at least one number
- No spaces
- It must be different from the temporary password

Warning: Never share your password with anybody, not even your manager. Nobody from Suma Soft will ever ask for it.

Note: If you type a wrong password 5 times, your account is locked for 15 minutes. Wait 15 minutes and try again, or ask the Super Admin.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$change-password$kb$, $kb$Getting started$kb$, $kb$Change your password$kb$, $kb$How to change your own password at any time from Settings.$kb$, $kb$change password reset password new password update password forgot password badalna settings my account$kb$, 30,
$kb$## Change your own password
1. Click Settings in the left menu.
2. In the "My account" box, type your current password.
3. Type the new password two times.
4. Press Change password.

The new password must have at least 10 characters, with at least one letter and one number, and no spaces.

## Forgot your password?
You cannot see your old password anywhere, because the tool never stores passwords in readable form. Ask the Super Admin to set a new temporary password for you (Team Management, open your name, Login password, Set password). Then sign in and choose your own password again.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$menu-guide$kb$, $kb$Getting started$kb$, $kb$What each menu item does$kb$, $kb$A short guide to every item in the left menu.$kb$, $kb$menu sidebar navigation left side pages sections where is option kahan hai items list$kb$, 40,
$kb$## The left menu
Not everybody sees every item. What you see depends on your role.

- Dashboard: numbers and charts of tickets for any dates you choose.
- Client Management: add, edit and delete clients (Super Admin only).
- Project Management: projects of each client, their support levels and ticket tool.
- Integration Management: upload the daily Excel sheet, see the list of connected tools.
- Ticket Management: find any ticket, change its status, assign it, escalate it.
- Team Management: create employees, give them projects, set shift and weekly off, see workload (Super Admin only).
- SLA Management: targets for reply and solve time, and the late tickets.
- Automation: rules that run by themselves every 5 minutes (Super Admin only).
- Reports: project-wise summary that you can download.
- Notifications: your alerts. A red number shows how many are unread.
- Knowledge Base: this help section.
- Security: roles and the audit log of every change (Super Admin only).
- Settings: your password, and tool-wide settings for the Super Admin.

Tip: At the bottom of the menu you see your name and role, and the Log out button.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$roles-who-sees-what$kb$, $kb$Getting started$kb$, $kb$Roles: who can see and do what$kb$, $kb$Super Admin, Project Manager, Team Leader, L1, L2, L3 and other roles explained.$kb$, $kb$role roles permission access rights who can see what super admin project manager team leader l1 l2 l3 management viewer client admin agent lead$kb$, 50,
$kb$## The roles
- Super Admin: can do everything. Adds clients, projects and employees, changes settings.
- Management Viewer: can see all dashboards and reports, but cannot change anything.
- Client Admin: manages the clients given to them and those projects.
- Project Manager: manages their own projects, the project team and imports.
- Team Leader: sees their projects and assigns tickets.
- L1 Support, L2 Support, L3 Support: work on tickets of the projects they are in.

## Projects decide what you see
An employee sees only the tickets of the projects they are added to. For example, if Kunal is added only to Codility, he will not see SouthCo tickets.

## New roles
The Super Admin can create new roles, like Project Lead. A new role works like one of two types:
- Lead type: sees and leads the projects they are on.
- Agent type: works on tickets of their projects.

See "Create a new role" for the steps.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$daily-routine$kb$, $kb$Getting started$kb$, $kb$Daily routine: start the tool every morning$kb$, $kb$The exact commands to start the backend and the website on your computer every day.$kb$, $kb$start run open daily morning routine commands vs code terminal backend frontend npm mvnw start-backend start-frontend chalu kaise kare launch$kb$, 60,
$kb$## You need two terminals
The tool has two parts. Both must keep running while you use it:
- The backend (the server, it talks to the database)
- The website (what you see in Chrome)

## Steps
1. Open VS Code.
2. Open a terminal (Terminal menu, New Terminal). Paste this and press Enter:
   powershell -ExecutionPolicy Bypass -File C:\Dev\SumaTracking\scripts\start-backend.ps1
3. Type the database password when asked. Nothing shows while you type. This is normal.
4. Wait until you see: Started SumaTrackingBackendApplication
5. Open a second terminal with the + button. Paste this and press Enter:
   powershell -ExecutionPolicy Bypass -File C:\Dev\SumaTracking\scripts\start-frontend.ps1
6. Open http://localhost:5173 in Chrome and sign in.

## Rules
- Do not close these two terminals while you work.
- Use a third terminal for git commands.
- To stop everything, press Ctrl+C in both terminals.

Tip: If the password is wrong 3 times, run the reset-db-password script. See "Backend says password authentication failed".$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$find-a-ticket$kb$, $kb$Tickets$kb$, $kb$Find a ticket by number$kb$, $kb$Search any ticket with its number, like 124628 for Codility or C-7781 for SouthCo.$kb$, $kb$find ticket search ticket open ticket ticket number id lookup where is my ticket dhundhe dhundna codility zendesk number southco case no$kb$, 70,
$kb$## Steps
1. Click Ticket Management in the left menu.
2. In the search box, type the ticket number. Examples: 124628 (Codility, Zendesk number) or TK-000012 (a ticket made inside this tool).
3. Press Search.
4. Click the ticket in the list. A panel opens on the right with all details.

## Narrow the list
- Use the quick buttons at the top: All tickets, Pending, Open, Escalated, Resolved, Closed.
- Choose a Project to see only that project.
- Use Created from and to dates.
- Press Clear to remove all filters.

Note: You only see tickets of the projects you are added to. If you cannot find a ticket, check that it belongs to your project, and that the latest sheet was imported.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$ticket-statuses$kb$, $kb$Tickets$kb$, $kb$What each ticket status means$kb$, $kb$Open, In Progress, Pending, Pending L2, Resolved, Closed, On Hold, Merged explained simply.$kb$, $kb$status meaning open pending resolved closed solved hold merged in progress pending l2 tier 2 waiting user third party stage state$kb$, 80,
$kb$## Statuses
- Open: new ticket, nobody has replied yet.
- In Progress (In Process): somebody is working on it.
- Pending - User: we replied, now we are waiting for the customer's answer.
- Pending - L1: waiting for the L1 team.
- Pending - L2 (Tier 2): escalated to the second level team.
- Pending - Third party: waiting for some other company or team.
- On Hold: stopped for some time on purpose.
- Resolved: solved.
- Closed: finished and closed.
- Merged: joined into another ticket (it counts as finished).

## How sheet words become statuses
Codility sheet: Solved becomes Resolved, Pending becomes Pending - User, Open becomes Pending - L2 (because in Codility, Open means escalated to Tier 2).

SouthCo sheet: Open becomes Pending - L2, Pending and In Process mean we answered and are waiting for the customer, Solved becomes Resolved, Closed stays Closed, Hold becomes On Hold, Merged stays Merged.

## Groups used in counts
- Open or in progress: Open, In Progress
- Waiting: all the Pending statuses and On Hold
- Done: Resolved, Closed, Merged$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$change-ticket-status$kb$, $kb$Tickets$kb$, $kb$Change the status of a ticket$kb$, $kb$How to mark a ticket as in progress, pending, resolved or closed.$kb$, $kb$change status update status mark resolved close ticket solve ticket complete ticket status badle$kb$, 90,
$kb$## Steps
1. Open the ticket (Ticket Management, search, click the ticket).
2. In the right panel, find Change status.
3. Choose the new status from the list.
4. If you want, write a short note in "Note for the history", for example "Customer confirmed it works".
5. Press the Change status button.

The change is saved in the ticket History with your name and the time.

Note: Who can change what is checked by the server. An agent can work only on tickets of their own projects.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$assign-ticket$kb$, $kb$Tickets$kb$, $kb$Assign or reassign a ticket$kb$, $kb$Give a ticket to an employee, or move it to another employee.$kb$, $kb$assign reassign give ticket transfer ticket allocate ticket handover owner assignee change assignee kisko dena$kb$, 100,
$kb$## Steps
1. Open the ticket.
2. In the right panel, choose the employee in the Assigned to list. Only employees of that project are shown.
3. Add a note if you want.
4. Press Assign (or Reassign if somebody already has it).

The employee gets a notification within 5 minutes, if the Assignment alerts rule is on.

## Auto-assign
The Super Admin can switch on "Auto-assign new tickets" in Automation. Then every new unassigned open ticket goes to the L1 member of that project with the fewest open tickets, who is not on weekly off today.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$escalate-ticket$kb$, $kb$Tickets$kb$, $kb$Escalate a ticket to Tier 2$kb$, $kb$Move a ticket to the L2 (Tier 2) team when L1 cannot solve it.$kb$, $kb$escalate escalation tier 2 l2 level 2 senior team forward ticket move up higher level$kb$, 110,
$kb$## When to escalate
Escalate when the L1 team cannot solve the problem and the second level team (L2, Tier 2) must look at it.

## Steps
1. Open the ticket.
2. Write a short note explaining why (optional but helpful).
3. Press Escalate to Tier 2.

## What happens
- The ticket is marked Escalated: Yes.
- The status becomes Pending - L2.
- The project leads and the assignee get a notification.
- The change is saved in the ticket History and in the audit log.

Note: A finished ticket (Resolved or Closed) cannot be escalated.

Tip: The Super Admin can switch on "Auto-escalate on SLA breach". Then a ticket that breaks its resolution time is escalated automatically.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$ticket-history$kb$, $kb$Tickets$kb$, $kb$See the history of a ticket$kb$, $kb$Who changed what and when on a ticket.$kb$, $kb$history timeline log changes who changed ticket activity audit trail purana$kb$, 120,
$kb$## Steps
1. Open the ticket.
2. Scroll down in the right panel to History.

Every change is listed: when it was imported, status changes, assignments, escalations and notes. Each line shows the time and who did it (or "Import" if it came from the Excel sheet).

Note: History can never be edited or deleted.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$import-daily-sheet$kb$, $kb$Import (Excel sheets)$kb$, $kb$Upload the daily Excel sheet$kb$, $kb$Step by step: check the sheet first, then import the tickets.$kb$, $kb$import upload excel sheet xlsx daily sheet monthly sheet nov26 dec26 load data add tickets from excel integration check file import tickets kaise upload kare$kb$, 130,
$kb$## Before you start
- Keep the latest copy of the sheet on your computer, for example "Codility Log Files.xlsx".
- Know the sheet (tab) name of the month, for example Nov26.

## Steps
1. Click Integration Management, then the Excel import tab.
2. Choose the Project (Codility or SouthCo).
3. Choose the File.
4. Type the Sheet name exactly as it is on the Excel tab, for example Nov26.
5. Press Check file. Nothing is saved yet. You see how many tickets would be created, updated or skipped.
6. If it looks right, press Import tickets.
7. Wait until it says Import finished.

## Every day
Upload the same month's sheet again every day. Only new and changed tickets are touched. On 1st of a new month, use the new sheet name (Dec26, Jan27 and so on).

Note: Import tickets works only after a successful Check file of the same file and sheet.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$import-no-duplicates$kb$, $kb$Import (Excel sheets)$kb$, $kb$Why the same sheet never makes double tickets$kb$, $kb$How the tool knows a ticket already exists.$kb$, $kb$duplicate double tickets same sheet again re-import twice repeated copy$kb$, 140,
$kb$## How it works
Each ticket is known by three things together: the project, the tool (Zendesk or Keypanion) and the Ticket ID from the sheet.

When you import the sheet again:
- A Ticket ID that is new: a new ticket is created.
- A Ticket ID that exists and something changed (status, assignee, closing time...): the ticket is updated, and the change goes into History.
- A Ticket ID that exists and nothing changed: it is left as it is.

So you can safely upload the same sheet many times a day.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$import-errors$kb$, $kb$Import (Excel sheets)$kb$, $kb$Import shows errors or warnings$kb$, $kb$What skipped rows, errors and warnings mean, and how to fix them.$kb$, $kb$import error warning skipped rows failed import problem wrong date unknown status bad row fix sheet file not found sheet not found$kb$, 150,
$kb$## After Check file you may see
- Created / Updated / Unchanged: all fine.
- Rows skipped (errors): these rows were not saved. The table shows the row number, the column, the cell value and what to fix.
- Warnings: the row was saved, but something looked odd (for example an unknown topic).

## Common problems
- Sheet not found: the sheet name does not match the Excel tab. Check spelling and capital letters, for example Nov26.
- Unknown status or priority: the word in the sheet is new. Tell the Super Admin; it must be added to the mapping.
- Wrong date: the date cell is empty or not a real date. Fix it in Excel.
- Empty template rows at the bottom are skipped silently. They are not errors.

## Steps to fix
1. Open the Excel file and correct the rows shown.
2. Save the file.
3. Press Check file again, then Import tickets.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$import-columns$kb$, $kb$Import (Excel sheets)$kb$, $kb$Which columns the sheet must have$kb$, $kb$The column names the tool reads from the Codility and SouthCo sheets.$kb$, $kb$columns headings headers format template sheet format fields case no ticket id assignee date of creation$kb$, 160,
$kb$## Codility sheet
Ticket ID, Date of Creation, Timeframe, Time of Creation, Assignee, Day, Escalated (Yes/No), Date of Closure, Time of Closure, Resolution Time, Closed By, Status, Satisfaction Rating, Priority, Source, Issue Type, Plan, Topic, Category, Subcategory, Requester, Requester Role.

## SouthCo sheet
Same idea, with these names: Case No (Ticket ID), Case Owner (Assignee), Scenario (Topic), Issue Description (Description).

## Dates and times
- Dates are read as month/day/year. When a date is unclear, the Day column decides.
- Times are India time (IST).

Note: Do not rename the column headings. If a new column is needed, tell the Super Admin.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$assignee-not-linked$kb$, $kb$Import (Excel sheets)$kb$, $kb$Agent name in the sheet is not linked to an employee$kb$, $kb$Why a ticket shows a name but no employee, and how to link it.$kb$, $kb$assignee not linked name not matching employee missing agent workload zero unlinked$kb$, 170,
$kb$## Why it happens
A name in the Assignee column is linked to an employee only when:
- that employee is ACTIVE, and
- the employee is a member of that project, and
- the name matches the full name, or the first name if only one member has it.

## Steps to fix
1. Team Management: make sure the employee exists with the correct full name.
2. Open the employee and allocate the project (Codility or SouthCo).
3. Import the month's sheet again (Check file, then Import tickets).

Now the tickets are linked, and the workload numbers show correctly.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$add-client$kb$, $kb$Clients and projects$kb$, $kb$Add a new client$kb$, $kb$Create a client with name, designation, email, contact number and project.$kb$, $kb$add client new client create client customer company register client form$kb$, 180,
$kb$## Steps (Super Admin)
1. Click Client Management.
2. Press Create client. A panel opens on the right.
3. Fill the fields:
   - Name (required)
   - Designation, for example IT Manager
   - Company Email ID (required), for example name@company.com
   - Contact Number (required), exactly 10 digits, for example 9876543210
   - Project Name: a new project with this name is created for the client
   - Account Manager (Is Assigned): Yes or No. If Yes, choose the employee.
4. Press Add.

The client appears in the table at once.

## Rules
- The email must look like name@company.com.
- The contact number must be exactly 10 digits, numbers only, not starting with 0. Do not type +91 or spaces.
- Two clients cannot have the same name.

If something is wrong, the box turns red and a red message says what to fix.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$edit-delete-client$kb$, $kb$Clients and projects$kb$, $kb$Edit, delete or restore a client$kb$, $kb$Change client details, delete a client and bring it back.$kb$, $kb$edit client update client change client details phone number contact number email designation account manager delete client remove client restore client undo delete deleted clients$kb$, 190,
$kb$## Edit
1. Client Management.
2. Press the pencil icon on the right of the client's row.
3. Change the details and press Update.

## Delete
1. Press the bin icon on the right of the row.
2. The tool asks "Are you sure?". Press Delete, or Cancel to go back.

The client disappears from the list, but nothing is really erased. Its projects, tickets and history stay safe.

## Restore
1. Tick "Show deleted clients".
2. Press Restore on the client's row.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$create-project$kb$, $kb$Clients and projects$kb$, $kb$Create or change a project$kb$, $kb$Add a project for a client and set support levels, ticket tool and time zone.$kb$, $kb$create project new project add project edit project support levels ticketing tool time zone start date end date$kb$, 200,
$kb$## Steps
1. Click Project Management.
2. Press Create project.
3. Fill in:
   - Client (required)
   - Project name (required)
   - Description
   - Support levels: tick L1, L2 and/or L3 (at least one)
   - Ticketing tool, for example ZENDESK or KEYPANION (leave empty if only Excel)
   - Time zone, for example Asia/Kolkata
   - Support start and end date (end cannot be before start)
4. Press Add.

To change a project, press the pencil icon on its row, change the details and press Update.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$project-status$kb$, $kb$Clients and projects$kb$, $kb$Deactivate, activate or close a project$kb$, $kb$Pause a project, start it again, or close it for ever.$kb$, $kb$deactivate project activate project close project pause stop project inactive closed status$kb$, 210,
$kb$## The three actions
- Deactivate: the project is paused. No imports or team changes until it is activated again.
- Activate: the project works again.
- Close: the project is finished for ever. It cannot be changed or reopened. Its tickets and history stay.

Each action asks "Are you sure?" first.

Warning: Close only when the client contract is really over.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$add-employee$kb$, $kb$Team$kb$, $kb$Add a new employee$kb$, $kb$Create an employee account with role, projects and a temporary password.$kb$, $kb$add employee new employee create employee create user add agent staff member karmachari onboard joining$kb$, 220,
$kb$## Steps (Super Admin)
1. Click Team Management.
2. Press Create employee.
3. Fill in:
   - Full name, for example Himanshu Thorat (letters only)
   - Employee code, for example 1001
   - Company email (this is the login name), for example himanshu.thorat@sumasoft.com
   - Contact number: 10 digits (optional)
   - Role, for example L1 Support
   - Reporting manager, designation, department, shift, weekly off, skills (optional)
4. Press Add.

The panel stays open. Now finish the two last parts:

## Give projects
1. In Project allocation, choose the project (for example Codility).
2. Keep Member and choose the level (L1, L2 or L3).
3. Press Allocate. Repeat for every project.

## Give a password
1. In Login password, press Generate (or type one).
2. Press Set password.
3. Give this password to the employee privately. They must change it at first login.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$shift-weekly-off$kb$, $kb$Team$kb$, $kb$Set shift timings and weekly off$kb$, $kb$Tell the tool when an employee works and which days they are off.$kb$, $kb$shift timing working hours weekly off holiday off day roster schedule leave week off$kb$, 230,
$kb$## Steps
1. Team Management, press the pencil icon on the employee.
2. Set Shift starts and Shift ends, for example 09:00 and 18:00.
3. Under Weekly off, click the days the employee does not work, for example Sun. Selected days turn dark.
4. Press Update.

Auto-assign never gives new tickets to an employee on their weekly off day.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$workload$kb$, $kb$Team$kb$, $kb$Understand the workload numbers$kb$, $kb$Open, Waiting, Escalated open, Done in 30 days and Oldest open explained.$kb$, $kb$workload productivity performance numbers open waiting oldest open escalated open how busy agent load$kb$, 240,
$kb$## The columns in Team Management
- Open: tickets assigned to the person that are open or in progress.
- Waiting: their tickets waiting for the customer or another team.
- Done, 30 days: tickets they finished in the last 30 days.
- Escalated open: their escalated tickets that are still not finished.
- Oldest open: how long their oldest unfinished ticket has been waiting.

Use these numbers to share the work fairly.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$deactivate-employee$kb$, $kb$Team$kb$, $kb$Deactivate an employee who left$kb$, $kb$Stop an employee from signing in without losing their history.$kb$, $kb$deactivate employee remove employee delete employee left company resign inactive block user disable$kb$, 250,
$kb$## Steps
1. Team Management.
2. Find the employee and press Deactivate.
3. Confirm.

The employee can no longer sign in and gets no new notifications. Their tickets, history and reports stay. You can press Activate later if they come back.

Note: Employees are never deleted, so that old reports stay correct.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$what-is-sla$kb$, $kb$SLA$kb$, $kb$What is SLA?$kb$, $kb$SLA means the promised time to reply and to solve a ticket.$kb$, $kb$sla meaning service level agreement deadline promise time limit target late overdue what is sla$kb$, 260,
$kb$## In simple words
SLA is the time we promise the client. There are two clocks:
- First response: how fast we send the first reply.
- Resolution: how fast we solve the ticket.

The time depends on the priority. A Critical ticket gets less time than a Low ticket.

## Starting targets
- Critical: reply in 1 hour, solve in 4 hours
- High: reply in 2 hours, solve in 8 hours
- Medium: reply in 4 hours, solve in 24 hours
- Low: reply in 8 hours, solve in 48 hours

The Super Admin can change these for every project.

## The states
- On track: there is enough time left.
- At risk: most of the time is used (80% by default). Hurry.
- Breached: the time is over. The promise is broken.
- Paused: the ticket is waiting for the customer, so the clock is stopped.
- Met: the ticket was finished in time.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$sla-overview$kb$, $kb$SLA$kb$, $kb$Read the SLA overview page$kb$, $kb$SLA percentage per project and the list of late tickets.$kb$, $kb$sla overview sla page percentage compliance late tickets breached list at risk list attention$kb$, 270,
$kb$## Steps
1. Click SLA Management. The SLA overview tab opens.
2. Choose a project or All projects, and the Created from and to dates.

## What you see
- For each project, "First response met" and "Resolved within SLA" in percent. Green is good (90% or more), orange is OK, red is bad.
- How many open tickets are breached, at risk, on track and paused.
- Below: "Open tickets that need attention". Breached tickets come first, then at risk. Each row shows the time used, the target and the due time.

Tip: Click Breached or At risk above the table to see only those tickets. Work on the breached ones first.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$sla-settings$kb$, $kb$SLA$kb$, $kb$Change SLA targets, hours and holidays$kb$, $kb$Set reply and solve time per priority, business hours, holidays and the warning level.$kb$, $kb$change sla target set sla hours business hours 24x7 holidays warning level pause clock working days sla settings configure$kb$, 280,
$kb$## Steps (Super Admin)
1. SLA Management, open the tab Targets and support hours.
2. Choose the project.
3. Targets per priority: type the hours. 0.5 means 30 minutes, 24 means one day.
4. Support hours:
   - 24 x 7: every hour counts, day and night.
   - Business hours only: choose working days and the start and end time.
5. Warning level: at this percent the ticket shows "At risk" and alerts go out (default 80).
6. Pause: tick it to stop the clock while the ticket waits on the customer.
7. Holidays (only for business hours): press Add holiday, choose the date and type the name, for example Diwali.
8. Press Save SLA settings.

## Rules
- Resolution time cannot be less than first response time.
- Day end must be after day start.
- A holiday date can be added only once.

Every change is saved in the audit log.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$why-sla-breached$kb$, $kb$SLA$kb$, $kb$Why does my ticket show Breached?$kb$, $kb$How the SLA time is counted for a ticket.$kb$, $kb$why breached late wrong sla how counted clock calculation time used elapsed$kb$, 290,
$kb$## How time is counted
- The clock starts when the ticket was created (time from Zendesk, Keypanion or the sheet).
- First response stops at the first reply time. If no reply time is recorded, a finished ticket uses its solve time.
- Resolution stops when the ticket is Resolved or Closed.
- For business hours projects, only working hours count. Weekly offs and holidays do not count.
- While a ticket waits on the customer, it shows Paused.

## Check
1. Open the ticket and look at the created time and the priority.
2. Look at the project's targets in SLA Management.

Note: The Excel sheet does not say when a ticket was waiting, so for finished tickets the waiting time is also counted.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$notifications$kb$, $kb$Notifications and automation$kb$, $kb$Your notifications$kb$, $kb$What alerts you get and how to mark them as read.$kb$, $kb$notification notifications alert alerts bell message unread red number reminder mark read$kb$, 300,
$kb$## You get a notification when
- A ticket you work on is about to break its SLA (At risk) or has broken it (Breached).
- A ticket is assigned to you.
- A ticket of your project is escalated.
- Every morning after 9 AM: the daily summary (Super Admins and Project Managers).

## Steps
1. Click Notifications. The red number in the menu shows how many are unread.
2. Read the message. It shows the project and the ticket number.
3. Press Mark read, or Mark all as read.

Tip: New notifications arrive within 5 minutes. Press Refresh to check again.

Note: Notifications are inside the tool only. Email will come later.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$automation-rules$kb$, $kb$Notifications and automation$kb$, $kb$Automation rules explained$kb$, $kb$The five rules that run by themselves every 5 minutes.$kb$, $kb$automation rules auto assign auto escalate daily summary sla alerts assignment alerts switch on off scheduler automatic$kb$, 310,
$kb$## The rules (Super Admin can switch each on or off)
- SLA alerts: tells the assignee and project leads when a ticket is at risk or breached. Super Admins hear about breaches.
- Assignment alerts: tells an employee when a ticket is given to them.
- Auto-escalate on SLA breach: when the resolution time is over, the ticket is escalated to Tier 2 automatically.
- Auto-assign new tickets: a new unassigned open ticket goes to the L1 member with the fewest open tickets.
- Daily summary: every morning after 9 AM, yesterday's numbers per project.

## Run now
Press Run now to run all switched-on rules at once. Click any run in Run history to see exactly what it did.

Note: Only tickets of the last 30 days get alerts. Each alert is sent only once per ticket.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$dashboard$kb$, $kb$Reports and dashboard$kb$, $kb$Read the dashboard$kb$, $kb$Ticket totals, charts and agent numbers for any period.$kb$, $kb$dashboard charts graphs numbers kpi totals home page summary today this month period agents topics$kb$, 320,
$kb$## Steps
1. Click Dashboard.
2. Choose the Period: Today, This month or Custom (pick From and To).
3. Choose All projects or one project.

## What you see
- Totals: tickets, done, open or in progress, waiting, escalated.
- Typical solve time and how many were solved within 1 hour.
- Tickets per day, per hour, per project, per status.
- Agents: how many tickets each person handled.
- How tickets arrive (email, chat, call) and the top topics.

Tip: If the dashboard shows zero, check the dates and that the sheet was imported.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$reports$kb$, $kb$Reports and dashboard$kb$, $kb$Download a project report$kb$, $kb$Project-wise summary for any dates, as a CSV file for Excel.$kb$, $kb$report reports download export csv excel project report monthly report summary file$kb$, 330,
$kb$## Steps
1. Click Reports.
2. Choose From and To dates.
3. The table shows each project: tickets, done, open, waiting, escalated, typical solve time and busiest agent.
4. Press Download CSV. Open the file in Excel.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$create-role$kb$, $kb$Security and settings$kb$, $kb$Create a new role$kb$, $kb$Make roles like Project Lead or Manager.$kb$, $kb$create role new role add role project lead manager designation permission security$kb$, 340,
$kb$## Steps (Super Admin)
1. Click Security, then Roles and permissions.
2. Press Create role.
3. Type the Role name, for example Project Lead.
4. Choose Works like:
   - Lead or manager: sees and leads the projects they are on.
   - Support agent: works tickets of their projects.
5. Add a description if you want and press Add.

Now you can choose this role when you create or edit an employee.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$audit-log$kb$, $kb$Security and settings$kb$, $kb$See who changed what (audit log)$kb$, $kb$Every important change is recorded and cannot be deleted.$kb$, $kb$audit log who changed history security track changes record activity logs$kb$, 350,
$kb$## Steps (Super Admin)
1. Click Security, then Audit log.
2. Choose the type of record to filter, or see everything.

Each line shows when, who, what action, and the value before and after. For example: client added, employee deactivated, SLA settings changed, sheet imported.

Note: Nobody can edit or delete audit records.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$settings-page$kb$, $kb$Security and settings$kb$, $kb$Settings page$kb$, $kb$Your password, and tool-wide settings for the Super Admin.$kb$, $kb$settings configuration preferences company name default time zone sla defaults automation limits$kb$, 360,
$kb$## Everyone
- My account: your name, email and role, and Change password.

## Super Admin only
- Organisation: company name and the default time zone for new projects.
- SLA starting targets: the targets a new project gets.
- Automation: how many days back alerts look, the most notifications per run, and the hour of the daily summary.
- Ticket statuses and priorities: a list for reference.

Press Save settings after changing. Every change goes into the audit log.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$cannot-sign-in$kb$, $kb$Problems and fixes$kb$, $kb$I cannot sign in$kb$, $kb$Wrong password, locked account or "Cannot reach the server".$kb$, $kb$cannot login cant sign in login not working wrong password locked account blocked error login failed login nahi ho raha$kb$, 370,
$kb$## Check these one by one
1. Is the email your full company email, like name@sumasoft.com?
2. Is Caps Lock off?
3. After 5 wrong passwords the account is locked for 15 minutes. Wait and try again.
4. Forgot the password? Ask the Super Admin for a new temporary password.
5. Message "Cannot reach the server": the backend is not running. See "The website says Cannot reach the server".$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$cannot-reach-server$kb$, $kb$Problems and fixes$kb$, $kb$The website says "Cannot reach the server"$kb$, $kb$The backend is stopped. How to start it again (ECONNREFUSED).$kb$, $kb$cannot reach server econnrefused proxy error backend not running 8080 http proxy error login error connection refused$kb$, 380,
$kb$## Why
The website runs, but the backend (server) is not running. The terminal of the website may show "http proxy error ... ECONNREFUSED".

## Steps
1. Look at the backend terminal. If it shows BUILD FAILURE or red errors, it stopped.
2. Start it again:
   powershell -ExecutionPolicy Bypass -File C:\Dev\SumaTracking\scripts\start-backend.ps1
3. Wait for: Started SumaTrackingBackendApplication
4. Press F5 in Chrome and sign in again.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$db-password-failed$kb$, $kb$Problems and fixes$kb$, $kb$Backend says password authentication failed$kb$, $kb$The database password was typed wrong. How to type it again or reset it.$kb$, $kb$password authentication failed suma_app database password db password postgres wrong password backend not starting 28P01$kb$, 390,
$kb$## Why
The backend could not open the database because the database password was wrong.

## Steps
1. Start the backend with the start script. It checks the password first and lets you try 3 times:
   powershell -ExecutionPolicy Bypass -File C:\Dev\SumaTracking\scripts\start-backend.ps1
2. Forgot the password? Reset it:
   powershell -ExecutionPolicy Bypass -File C:\Dev\SumaTracking\scripts\reset-db-password.ps1
   First type the postgres password (chosen when PostgreSQL was installed), then the new password two times.
3. Start the backend again with the new password.

Warning: Never write the database password in any file or in chat.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$port-in-use$kb$, $kb$Problems and fixes$kb$, $kb$Port 8080 is already in use$kb$, $kb$An old backend is still running. How to stop it.$kb$, $kb$port 8080 already in use address in use backend already running stop old process$kb$, 400,
$kb$## Why
An old copy of the backend is still running in the background.

## Fix
The start-backend script stops the old one by itself. Just run:
   powershell -ExecutionPolicy Bypass -File C:\Dev\SumaTracking\scripts\start-backend.ps1

The website may also open on 5174 instead of 5173 when 5173 is busy. That is fine, use the address the terminal shows.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$red-field-errors$kb$, $kb$Problems and fixes$kb$, $kb$A form shows red boxes and does not save$kb$, $kb$What the red messages mean and how to fix them.$kb$, $kb$red error form not saving validation invalid email invalid phone contact number 10 digits wrong format fields marked in red$kb$, 410,
$kb$## Why
The tool checks every form before saving, so that wrong data never enters.

## Common messages
- "is not a valid email address": type it like name@company.com.
- "must be exactly 10 digits": type only 10 numbers, like 9876543210. No +91, no spaces, not starting with 0.
- "can contain letters, spaces...": remove numbers or symbols from a name.
- "is required": this box cannot be empty.

## Steps
1. Read the red message under each red box.
2. Correct the value. The message goes away when it is right.
3. Press Add or Update again.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$glossary$kb$, $kb$Words used in this tool$kb$, $kb$Words used in this tool (glossary)$kb$, $kb$Simple meanings of words like L1, Tier 2, escalation, assignee, first response.$kb$, $kb$glossary meaning words terms definitions dictionary abbreviations l1 l2 l3 tier escalation assignee requester sla first response resolution dry run$kb$, 420,
$kb$## Words
- Ticket: one problem or question from a customer.
- Client: the company we give support to, like Codility or SouthCo.
- Project: our support work for a client. One client can have many projects.
- Assignee: the employee who is working on the ticket.
- Requester: the customer who raised the ticket.
- L1, L2, L3: support levels. L1 answers first, L2 handles harder problems, L3 is the expert level.
- Tier 2: the same as L2.
- Escalate: send the ticket to a higher level team.
- SLA: the promised time to reply and to solve.
- First response: the first reply to the customer.
- Resolution: solving the ticket.
- Breached: the SLA time is over.
- Check file: test the sheet without saving anything.
- Import: save the tickets from the sheet.
- Audit log: the record of every change, which nobody can delete.
- Super Admin: the person who controls the whole tool.$kb$);

INSERT INTO kb_articles (slug, category, title, summary, keywords, sort_order, body) VALUES ($kb$knowledge-base-help$kb$, $kb$Getting started$kb$, $kb$How to use this Knowledge Base$kb$, $kb$Search with simple words, or ask in your own words.$kb$, $kb$how to search help knowledge base ask question find answer ask mode search tips$kb$, 430,
$kb$## Search
Type one or two simple words, for example: add employee, import, password, SLA. Spelling mistakes are fine.

## Ask in your own words
If search does not find it, press "Ask in your own words" and type your question like you talk, for example:
- how do i give ticket to another person
- client ka phone number kaise badle
- sheet upload karne pe error aa raha hai

It understands normal sentences, small spelling mistakes and common Hindi words, and shows the best matching part of a guide.

## Still no answer?
Your question is saved for the Super Admin, who adds a new guide for it.

Note: The Knowledge Base only explains how the tool works. It does not open or change tickets.$kb$);

INSERT INTO kb_article_versions (article_id, version_no, category, title, summary, body, keywords, status)
SELECT id, 1, category, title, summary, body, keywords, status FROM kb_articles;