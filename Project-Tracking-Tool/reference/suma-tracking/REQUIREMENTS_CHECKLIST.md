# Suma Tracking: Requirements Checklist

Last updated: 3 November 2026
How to read this file:
- **Src** = where the requirement comes from: FRD = FRD v1.0, XL = Excel "Admin Features" sheet, OR = original instructions.
- **Pri** = proposed priority (P1 = MVP, P2 = next release, P3 = future). These priorities are **not approved yet**.
- **Status** = Not started / In progress / Done.
- **Tested** = No / Yes (with the date and the test name).

Only mark an item "Done" after it has been tested.

## 0. Project setup
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| SET-01 | Tools installed (Java 21, Git, PostgreSQL 17, Node, VS Code, Postman) | OR | P1 | Done | Yes (versions checked) |
| SET-02 | Private GitHub repository created | XL wk1 | P1 | Done | Yes (push worked) |
| SET-03 | Spring Boot backend skeleton runs on 8080 | OR | P1 | Done | Yes (started) |
| SET-04 | React + Vite frontend skeleton runs on 5173 | OR | P1 | Done | Yes (started) |
| SET-05 | Database suma_tracking owned by suma_app | OR | P1 | Done | Yes (psql login) |
| SET-06 | Backend connected to the database (JPA, driver, Flyway), password kept outside Git | OR | P1 | Not started | No |
| SET-07 | Requirements finalised and approved | XL wk1 | P1 | In progress | - |
| SET-08 | Use-case diagrams | XL wk1 | P1 | Not started | - |
| SET-09 | Database architecture finalised | XL wk1 | P1 | Not started | - |
| SET-10 | UI wireframes | XL wk1 | P1 | Not started | - |

## 1. Users, roles and access (FRD 3)
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| FR-001 | Super Admin creates, edits, activates and deactivates user accounts | FRD | P1 | Not started | No |
| FR-002 | Role-based access permissions | FRD | P1 | Not started | No |
| FR-003 | Assign users to one or many projects | FRD | P1 | Not started | No |
| FR-004 | Users see only what their role and projects permit | FRD | P1 | Not started | No |
| FR-005 | User login and activity history | FRD/XL | P1 | Not started | No |
| FR-006 | Reset or revoke user access | FRD | P1 | Not started | No |
| FR-007 | Secure authentication and session management | FRD | P1 | Not started | No |
| ROLE-01 | Role hierarchy and permission matrix approved | OR | P1 | **Waiting for approval** | - |

## 2. Super Admin dashboard (FRD 4)
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| DSH-01 | KPI cards: Total Clients, Active Projects, Tickets Received, Open, In Progress, Pending, Resolved, SLA Compliance | FRD | P1 | Not started | No |
| DSH-02 | Monthly received vs resolved trend | FRD/OR | P1 | Not started | No |
| DSH-03 | Status distribution | FRD | P1 | Not started | No |
| DSH-04 | Client-wise statistics | FRD | P1 | Not started | No |
| DSH-05 | Project-wise statistics | FRD/OR | P1 | Not started | No |
| DSH-06 | Priority-wise distribution | FRD | P1 | Not started | No |
| DSH-07 | L1/L2/L3 team performance | FRD/XL | P1 | Not started | No |
| DSH-08 | Employee productivity | FRD | P1 | Not started | No |
| DSH-09 | SLA compliance trend | FRD | P1 | Not started | No |
| DSH-10 | Pending and ageing statistics; overdue tickets | FRD/XL | P1 | Not started | No |
| DSH-11 | Integration health | FRD | P1 | Not started | No |
| DSH-12 | Global filters: date, client, project, team, employee, priority, status, source | FRD | P1 | Not started | No |
| DSH-13 | Daily / weekly / monthly statistics | XL | P1 | Not started | No |
| DSH-14 | Team member count; escalated ticket count | OR | P1 | Not started | No |
| FR-008 | Total Clients card opens the client list | FRD | P1 | Not started | No |
| FR-009 | Active Projects card opens the active projects | FRD | P1 | Not started | No |
| FR-010 | Open Tickets card opens matching tickets | FRD | P1 | Not started | No |
| FR-011 | Charts refresh when filters change | FRD | P1 | Not started | No |
| FR-012 | Recent activities section | FRD | P1 | Not started | No |
| FR-013 | Critical SLA alerts and integration failures shown | FRD | P1 | Not started | No |
| DSH-15 | Dashboards for the other approved roles (scoped) | OR | P1 | Not started | No |
| DSH-16 | Never show sample numbers as real company data | OR | P1 | Not started | No |

## 3. Client management (FRD 5)
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| CLI-01 | Fields: Client ID (auto), Name, Company, Contact Person, Email, Phone, Address, Business Domain, Account Manager, Status, Onboarding Date, Remarks | FRD | P1 | Not started | No |
| FR-014 | Create client | FRD/XL | P1 | Not started | No |
| FR-015 | Edit client | FRD/XL | P1 | Not started | No |
| FR-016 | Activate / deactivate client | FRD/XL | P1 | Not started | No |
| FR-017 | Unique system-generated Client ID | FRD | P1 | Not started | No |
| FR-018 | Many projects per client | FRD | P1 | Not started | No |
| FR-019 | Client-wise ticket statistics | FRD | P1 | Not started | No |
| FR-020 | Client-wise project and employee allocation | FRD | P1 | Not started | No |
| FR-021 | Deactivation never deletes tickets or audit records | FRD | P1 | Not started | No |
| CLI-02 | Assign account managers | XL | P1 | Not started | No |
| CLI-03 | Configure client-specific requirements | XL | P2 | Not started | No |
| CLI-04 | Project-wise ticket history and reports from the client screen | XL | P1 | Not started | No |
| CLI-05 | Preserve original projects: Suma Tool, Hero, Codility, SouthCo (client mapping to confirm) | OR | P1 | Not started | No |

## 4. Project management (FRD 6)
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| PRJ-01 | Fields: ID, Name, Client, Description, PM, TL, Employees, Support Level, Support Start/End, Hours, Weekly Off, Status, SLA config, External System | FRD | P1 | Not started | No |
| FR-022 | Many projects under a client | FRD/XL | P1 | Not started | No |
| FR-023 | Assign Project Managers and Team Leaders | FRD/XL | P1 | Not started | No |
| FR-024 | Allocate employees to projects | FRD | P1 | Not started | No |
| FR-025 | Project support hours (and shifts) | FRD/XL | P1 | Not started | No |
| FR-026 | Project weekly offs and holidays | FRD | P1 | Not started | No |
| FR-027 | Project SLA policies | FRD/XL | P1 | Not started | No |
| FR-028 | Activate / deactivate / close project | FRD/XL | P1 | Not started | No |
| FR-029 | Project-wise historical reports | FRD | P1 | Not started | No |
| PRJ-02 | Configure project priorities | XL | P1 | Not started | No |
| PRJ-03 | Ticket categories and sub-categories per project | XL/OR | P1 | Not started | No |

## 5. User and team management (FRD 7)
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| USR-01 | Fields: Employee ID, Name, Email, Phone, Designation, Department, Skills, Role, Projects, Reporting Manager, Shift, Weekly Off, Status | FRD | P1 | Not started | No |
| FR-030 | Add / update employees | FRD/XL | P1 | Not started | No |
| FR-031 | Employees on multiple projects | FRD/XL | P1 | Not started | No |
| FR-032 | Assign L1 / L2 / L3 roles | FRD/XL | P1 | Not started | No |
| FR-033 | Configure employee shifts | FRD/XL | P1 | Not started | No |
| FR-034 | Weekly-off and holiday schedules | FRD/XL | P1 | Not started | No |
| FR-035 | Team Leaders view their team members | FRD | P1 | Not started | No |
| FR-036 | Employee-wise ticket allocation | FRD | P1 | Not started | No |
| FR-037 | Employee-wise resolved / pending / open counts | FRD | P1 | Not started | No |
| FR-038 | Employee workload visibility | FRD/XL | P1 | Not started | No |
| USR-02 | Configure user access permissions | XL | P1 | Not started | No |

## 6. Centralised ticket management (FRD 8)
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| TKT-01 | 15 FRD common fields | FRD | P1 | Not started | No |
| TKT-02 | All 23 original Excel columns preserved | OR | P1 | Not started | No |
| TKT-03 | Day, Timeframe and Resolution Time derived from timestamps; time-zone rules documented | OR | P1 | Not started | No |
| TKT-04 | Source-specific extra fields (JSONB only where useful) | OR | P1 | Not started | No |
| FR-039 | Retrieve tickets from configured external systems | FRD | P1 | Not started | No |
| FR-040 | One central ticket listing | FRD/XL | P1 | Not started | No |
| FR-041 | Keep original external ID and source | FRD | P1 | Not started | No |
| FR-042 | Every ticket linked to the correct client and project | FRD | P1 | Not started | No |
| FR-043 | Search by Ticket ID, Client, Project, Status | FRD | P1 | Not started | No |
| FR-044 | Filter by date, priority, category, status, assignee | FRD | P1 | Not started | No |
| FR-045 | Show created and last-updated timestamps | FRD | P1 | Not started | No |
| FR-046 | Ticket status history | FRD | P1 | Not started | No |
| FR-047 | Identify duplicates during sync | FRD | P1 | Not started | No |
| FR-048 | Ticket ageing | FRD/XL | P1 | Not started | No |
| FR-049 | Assign / reassign tickets | FRD/XL | P1 | Not started | No |
| FR-050 | Activity and assignment history | FRD/XL | P1 | Not started | No |
| TKT-05 | 8 default statuses; admins add statuses per project | FRD | P1 | Not started | No |
| TKT-06 | Unique (source system + external ID) database constraint | OR | P1 | Not started | No |
| TKT-07 | Create / update tickets where authorised | OR | P1 | Not started | No |
| TKT-08 | Escalate unresolved tickets | XL | P1 | Not started | No |
| TKT-09 | Track resolution and closure | XL | P1 | Not started | No |

## 7. Excel / CSV import and export
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| IMP-01 | Excel and CSV import of the 23-column file | OR | P1 | Not started | No |
| IMP-02 | Validate before import | OR | P1 | Not started | No |
| IMP-03 | Import error report | OR | P1 | Not started | No |
| IMP-04 | Duplicate detection on import | OR | P1 | Not started | No |
| IMP-05 | Import batches recorded in the audit history | OR | P1 | Not started | No |
| IMP-06 | Excel / CSV export of tickets | OR | P1 | Not started | No |
| IMP-07 | Secure file upload (type and size checks) | OR | P1 | Not started | No |

## 8. Integration management (FRD 9)
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| INT-01 | Config fields: name, client, project, platform, endpoint, auth method, credential reference, frequency, field mapping, status | FRD | P1 | Not started | No |
| INT-02 | Modular connector design (Zendesk, Keypanion, Salesforce, Jira, REST, Python/PHP, webhooks) | FRD/OR | P1 | Not started | No |
| INT-03 | First API integration: system to be decided; API access verified first | FRD/OR | P1 | **Waiting for decision** | No |
| INT-04 | More connectors | FRD | P2 | Not started | No |
| FR-051 | Register an external system | FRD | P1 | Not started | No |
| FR-052 | Store credentials securely | FRD/XL | P1 | Not started | No |
| FR-053 | Field mapping to the common ticket | FRD/XL | P1 | Not started | No |
| FR-054 | Scheduled synchronisation | FRD/XL | P1 | Not started | No |
| FR-055 | Webhook updates where available | FRD | P2 | Not started | No |
| FR-056 | Detect failed sync requests | FRD/XL | P1 | Not started | No |
| FR-057 | Integration execution logs | FRD/XL | P1 | Not started | No |
| FR-058 | Activate / deactivate integration | FRD/XL | P1 | Not started | No |
| FR-059 | No duplicates during sync | FRD | P1 | Not started | No |
| FR-060 | Integration health status | FRD | P1 | Not started | No |
| FR-061 | Preserve the source ticket ID | FRD | P1 | Not started | No |
| INT-05 | Pagination, rate-limit handling, safe retries | OR | P1 | Not started | No |

## 9. SLA management (FRD 10)
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| SLA-01 | Config: first response, resolution, priority, project / client SLA, working hours, holidays, escalation timelines | FRD/XL | P1 | Not started | No |
| FR-062 | Configure SLA rules | FRD | P1 | Not started | No |
| FR-063 | Use the project calendar and policy | FRD | P1 | Not started | No |
| FR-064 | Track first response time | FRD/XL | P1 | Not started | No |
| FR-065 | Track resolution time | FRD/XL | P1 | Not started | No |
| FR-066 | Find tickets approaching SLA | FRD | P1 | Not started | No |
| FR-067 | Find breached tickets | FRD/XL | P1 | Not started | No |
| FR-068 | Escalation alerts | FRD/XL | P1 | Not started | No |
| FR-069 | SLA compliance reports | FRD/XL | P1 | Not started | No |
| FR-070 | Business hours, holidays and approved pause conditions | FRD | P1 | Not started | No |

## 10. Reports (FRD 11)
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| RPT-01 | Daily, **Weekly**, Monthly ticket reports | FRD/XL | P1 | Not started | No |
| RPT-02 | Client-wise, Project-wise reports | FRD | P1 | Not started | No |
| RPT-03 | Employee productivity; L1/L2/L3 performance | FRD/XL | P1 | Not started | No |
| RPT-04 | Pending; Ageing | FRD/XL | P1 | Not started | No |
| RPT-05 | SLA compliance; SLA breach | FRD/XL | P1 | Not started | No |
| RPT-06 | Category; Integration failure | FRD | P1 | Not started | No |
| FR-071 | Date-range filter | FRD | P1 | Not started | No |
| FR-072 | Client and project filters | FRD | P1 | Not started | No |
| FR-073 | Employee and team filters | FRD | P1 | Not started | No |
| FR-074 | Excel and CSV export | FRD/XL | P1 | Not started | No |
| FR-075 | PDF export of management reports | FRD/XL | P2 | Not started | No |
| FR-076 | Graphs of key metrics | FRD | P1 | Not started | No |
| FR-077 | Scheduled reports and email (free/approved email only) | FRD/XL | P2 | Not started | No |
| RPT-07 | Reports respect roles and client isolation | OR | P1 | Not started | No |

## 11. Notifications and escalations (FRD 12)
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| FR-078 | Notify employee on assignment | FRD/XL | P1 | Not started | No |
| FR-079 | Notify Team Leaders about critical tickets | FRD/XL | P1 | Not started | No |
| FR-080 | Notify PMs about SLA breaches | FRD/XL | P1 | Not started | No |
| FR-081 | Configurable notification rules (per project) | FRD/XL | P2 | Not started | No |
| FR-082 | In-app notifications (P1); email (P2, needs approval) | FRD/XL | P1/P2 | Not started | No |
| FR-083 | Notification history | FRD | P1 | Not started | No |
| FR-084 | Configurable escalation levels | FRD/XL | P2 | Not started | No |

## 12. Workflow automation / RPA (FRD 13)
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| FR-085 | Configure basic automation rules | FRD | P2 | Not started | No |
| FR-086 | Auto-assignment by project or category | FRD/XL | P2 | Not started | No |
| FR-087 | Auto-escalation on SLA conditions | FRD | P2 | Not started | No |
| FR-088 | Scheduled report automation | FRD | P2 | Not started | No |
| FR-089 | Automation execution logs | FRD | P2 | Not started | No |
| FR-090 | Failed jobs identifiable and retryable | FRD | P2 | Not started | No |
| AUT-01 | Pending-ticket reminders | FRD/XL | P2 | Not started | No |
| AUT-02 | Auto-categorisation | FRD/XL | P2 | Not started | No |
| AUT-03 | Email-to-ticket | FRD | P2 | Not started | No |
| AUT-04 | Approval workflows | XL | P2 | Not started | No |
| AUT-05 | Custom business rules | XL | P2 | Not started | No |
| AUT-06 | Repetitive data-entry automation / RPA | FRD/XL | P3 | Not started | No |
| AUT-07 | Advanced RPA designer | FRD | P3 | Not started | No |

## 13. Knowledge base / SOP
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| KB-01 | Create and maintain SOPs | XL/OR | P2 | Not started | No |
| KB-02 | Upload troubleshooting documents | XL/OR | P2 | Not started | No |
| KB-03 | Project-specific FAQs | XL/OR | P2 | Not started | No |
| KB-04 | Access for support teams | XL/OR | P2 | Not started | No |
| KB-05 | Resolution steps for recurring issues | XL/OR | P2 | Not started | No |
| KB-06 | Track article usage | XL/OR | P2 | Not started | No |

## 14. Security and audit (FRD 14)
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| FR-091 | Role-based access control | FRD/XL | P1 | Not started | No |
| FR-092 | Client-level data isolation | FRD | P1 | Not started | No |
| FR-093 | No access to unauthorised clients / projects | FRD | P1 | Not started | No |
| FR-094 | Audit logs of important activity | FRD/XL | P1 | Not started | No |
| FR-095 | Integration credentials stored securely | FRD | P1 | Not started | No |
| FR-096 | Secure password and session management (BCrypt/Argon2, expiry, logout, revocation) | FRD/XL | P1 | Not started | No |
| FR-097 | Logs of administrative changes | FRD | P1 | Not started | No |
| FR-098 | Controlled access to sensitive reports | FRD | P1 | Not started | No |
| SEC-01 | Password and authentication policies configurable | XL | P2 | Not started | No |
| SEC-02 | Monitor unauthorised access attempts | XL | P1 | Not started | No |
| SEC-03 | Audit records append-only; previous/new values; actor; source | OR | P1 | Not started | No |
| SEC-04 | Validation, SQL-injection/XSS protection, CORS, CSRF where needed, rate limiting | OR | P1 | Not started | No |
| SEC-05 | No secrets in Git; least-privilege DB user | OR | P1 | In progress | Partly (.gitignore) |

## 15. System configuration (FRD 15)
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| CFG-01 | Ticket categories, priorities, statuses | FRD/XL | P1 | Not started | No |
| CFG-02 | Business and holiday calendars | FRD/XL | P1 | Not started | No |
| CFG-03 | Notification / email templates | FRD/XL | P2 | Not started | No |
| CFG-04 | Email configuration | FRD | P2 | Not started | No |
| CFG-05 | Default dashboard settings / widgets | FRD/XL | P2 | Not started | No |
| CFG-06 | Roles and permissions | FRD | P1 | Not started | No |
| CFG-07 | System-wide settings | FRD | P1 | Not started | No |
| CFG-08 | Custom ticket fields | XL/OR | P2 | Not started | No |
| CFG-09 | Data retention policies | XL/OR | P2 | Not started | No |
| FR-099 | Only authorised admins change configuration | FRD | P1 | Not started | No |
| FR-100 | Configuration change history | FRD | P1 | Not started | No |

## 16. Future enhancements (FRD 2.2)
| ID | Requirement | Src | Pri | Status | Tested |
|---|---|---|---|---|---|
| FUT-01 | AI ticket categorisation | FRD | P3 | Not started | No |
| FUT-02 | AI resolution recommendations | FRD | P3 | Not started | No |
| FUT-03 | Predictive SLA breach alerts | FRD | P3 | Not started | No |
| FUT-04 | Advanced workflow designer | FRD | P3 | Not started | No |
| FUT-05 | Client self-service portal | FRD | P3 | Not started | No |
| FUT-06 | Subscription and billing | FRD | P3 | Not started | No |

## 17. Non-functional targets (FRD 16; to be validated, not promised)
| ID | Target | Status | Tested |
|---|---|---|---|
| NFR-01 | Dashboard responds within 3 s under agreed load | Not started | No |
| NFR-02 | Many clients/projects in one deployment | Not started | No |
| NFR-03 | Tenant data isolation | Not started | No |
| NFR-04 | 99.5% monthly availability (not possible to promise on free hosting) | Not started | No |
| NFR-05 | Simple, consistent, responsive UI | Not started | No |
| NFR-06 | Current major desktop browsers | Not started | No |
| NFR-07 | Modular architecture | In progress | - |
| NFR-08 | Scheduled backups and a recovery procedure | Not started | No |
| NFR-09 | Integration retry, error logging, monitoring | Not started | No |

## 18. MVP acceptance criteria (FRD 19)
| # | Criterion | Met? |
|---|---|---|
| AC-01 | Super Admin logs in securely | No |
| AC-02 | Create and manage multiple clients | No |
| AC-03 | Multiple projects per client | No |
| AC-04 | Users allocated to projects with permissions | No |
| AC-05 | Connected to at least one approved external system | No |
| AC-06 | Integrated tickets visible on the central dashboard | No |
| AC-07 | Status and source kept correctly | No |
| AC-08 | Tickets linked to the correct client and project | No |
| AC-09 | Ticket statistics and reports visible | No |
| AC-10 | SLA monitoring and basic alerts work | No |
| AC-11 | Cross-client access prevented | No |
| AC-12 | QA and UAT done; no open critical defects | No |
| AC-13 | Technical Lead approves production readiness | No |
