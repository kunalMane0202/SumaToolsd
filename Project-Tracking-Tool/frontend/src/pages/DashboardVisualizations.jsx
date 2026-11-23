
import { useMemo } from "react";
import { motion } from "framer-motion";
import {
  Cell,
  Pie,
  PieChart,
  Tooltip,
} from "recharts";
import {
  freestyleRevealVariants,
  freestyleSpring,
  freestyleStaggerVariants,
} from "../motionPresets.js";
import "./Dashboard-visualizations.css";

const COLORS = [
  "#35bc85",
  "#e5a545",
  "#ff725e",
  "#a855f7",
  "#ff5b2e",
  "#8b8981",
];

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

function countByStatus(items, preferred = []) {
  const counts = new Map();

  safeArray(items).forEach((item) => {
    const status =
      String(item?.status || "Unspecified").trim() ||
      "Unspecified";

    counts.set(status, (counts.get(status) || 0) + 1);
  });

  return [
    ...preferred
      .filter((status) => counts.has(status))
      .map((status) => ({
        name: status,
        value: counts.get(status),
      })),

    ...Array.from(counts.entries())
      .filter(([status]) => !preferred.includes(status))
      .map(([name, value]) => ({ name, value })),
  ];
}

function getWorkloadName(item) {
  return (
    item?.name ||
    item?.memberName ||
    item?.userName ||
    item?.employeeName ||
    item?.assigneeName ||
    item?.username ||
    "Unassigned"
  );
}

function getWorkloadCount(item) {
  const possibleFields = [
    "open",
    "openTasks",
    "tasksOpen",
    "taskCount",
    "pendingTasks",
    "assignedTasks",
    "totalOpenTasks",
    "count",
  ];

  for (const field of possibleFields) {
    const value = Number(item?.[field]);

    if (
      item?.[field] !== null &&
      item?.[field] !== undefined &&
      item?.[field] !== "" &&
      Number.isFinite(value)
    ) {
      return Math.max(0, value);
    }
  }

  return 0;
}

// Animated dashboard statistics
export function AnimatedStat({ label, value, note, tone }) {
  return (
    <motion.div
      className={`stat ${tone ? `stat--${tone}` : ""}`}
      variants={freestyleRevealVariants}
      whileHover={{ y: -5, scale: 1.015, rotate: -0.5 }}
      whileTap={{ scale: 0.98 }}
      transition={freestyleSpring}
    >
      <p className="stat__value">
        <motion.span
          key={Number(value) || 0}
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
        >
          {(Number(value) || 0).toLocaleString("en-IN")}
        </motion.span>
      </p>

      <p className="stat__label">{label}</p>

      {note && <p className="stat__note">{note}</p>}
    </motion.div>
  );
}

// Reusable chart panel
function ChartPanel({ title, subtitle, children, wide = false }) {
  return (
    <motion.section
      className={`panel visual-panel ${
        wide ? "visual-panel--wide" : ""
      }`}
      variants={freestyleRevealVariants}
      whileHover={{ y: -4, rotate: -0.2 }}
      transition={freestyleSpring}
    >
      <div className="visual-panel__heading">
        <div>
          <h2>{title}</h2>
          <p>{subtitle}</p>
        </div>
      </div>

      {children}
    </motion.section>
  );
}

function EmptyState({ children }) {
  return (
    <div className="visual-empty">
      <span className="visual-empty__icon">▤</span>
      <p>{children}</p>
    </div>
  );
}

// Reusable CSS donut chart
function DonutChart({ data, label }) {
  const total = data.reduce(
    (sum, item) => sum + item.value,
    0
  );

  if (!total) {
    return (
      <EmptyState>No {label.toLowerCase()} data available.</EmptyState>
    );
  }

  return (
    <div
      className="visual-chart visual-chart--donut"
    >
      <div
        className="visual-chart__plot"
        role="img"
        aria-label={`${total} ${label.toLowerCase()} in total`}
      >
        <PieChart width={240} height={244}>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            cx="50%"
            cy="50%"
            innerRadius="62%"
            outerRadius="82%"
            paddingAngle={3}
            stroke="none"
            animationDuration={850}
          >
            {data.map((item, index) => (
              <Cell key={item.name} fill={COLORS[index % COLORS.length]} />
            ))}
          </Pie>
          <Tooltip
            formatter={(value, name) => [value, name]}
            contentStyle={{
              border: "1px solid var(--line)",
              borderRadius: 7,
              boxShadow: "var(--shadow-card)",
            }}
          />
        </PieChart>
      </div>
      <div className="visual-chart__center" aria-hidden="true">
        <strong>{total}</strong>
        <span>{label}</span>
      </div>
      <div className="visual-chart__legend">
        {data.map((item, index) => (
          <div className="visual-chart__legend-item" key={item.name}>
            <span style={{ "--legend-color": COLORS[index % COLORS.length] }} />
            <span>{item.name}</span>
            <strong>{item.value}</strong>
          </div>
        ))}
      </div>
    </div>
  );
}

export default function DashboardVisualizations({
  projects = [],
  tasks = [],
  workload = [],
  userRole,
}) {
  const safeProjects = safeArray(projects);
  const safeTasks = safeArray(tasks);
  const safeWorkload = safeArray(workload);

  // Project status
  const projectData = useMemo(
    () =>
      countByStatus(safeProjects, [
        "On track",
        "At risk",
        "Delayed",
        "Completed",
      ]),
    [projects]
  );

  // Task distribution
  const taskData = useMemo(
    () =>
      countByStatus(safeTasks, [
        "To do",
        "Open",
        "In progress",
        "Review",
        "Completed",
        "Done",
      ]),
    [tasks]
  );

  // Team workload
  const workloadData = useMemo(
    () =>
      safeWorkload
        .map((item) => ({
          name: getWorkloadName(item),
          open: getWorkloadCount(item),
        }))
        .sort((a, b) => b.open - a.open)
        .slice(0, 8),
    [workload]
  );

  const maxLoad = Math.max(1, ...workloadData.map((item) => item.open));

  return (
    <motion.div
      className="dashboard-visuals"
      initial="hidden"
      animate="visible"
      variants={freestyleStaggerVariants}
    >

      {/* PROJECT STATUS */}
      <ChartPanel
        title="Project Status"
        subtitle={`${safeProjects.length} accessible projects`}
      >
        {projectData.length > 0 ? (
          <DonutChart
            data={projectData}
            label="Projects"
          />
        ) : (
          <EmptyState>
            Project status data will appear when projects are available.
          </EmptyState>
        )}
      </ChartPanel>

      {/* TASK DISTRIBUTION */}
      <ChartPanel
        title="Task Distribution"
        subtitle={`${safeTasks.length} assigned tasks`}
      >
        {taskData.length > 0 ? (
          <DonutChart
            data={taskData}
            label="Tasks"
          />
        ) : (
          <EmptyState>
            Task status data will appear when tasks are assigned.
          </EmptyState>
        )}
      </ChartPanel>

      {/* TEAM WORKLOAD */}
      {userRole !== "Project Lead" && (
        <ChartPanel
          title="Team Workload"
          subtitle="Open tasks by team member"
          wide
        >
          {workloadData.some((item) => item.open > 0) ? (
            <div className="visual-workload">
              {workloadData
                .filter((item) => item.open > 0)
                .map((item, index) => (
                  <div
                    className="visual-workload__item"
                    key={`${item.name}-${index}`}
                  >
                    <div className="visual-workload__top">
                      <span className="visual-workload__name">{item.name}</span>
                      <strong>{item.open}</strong>
                    </div>
                    <div className="visual-workload__track">
                      <motion.div
                        className="visual-workload__fill"
                        initial={{ width: 0 }}
                        animate={{ width: `${(item.open / maxLoad) * 100}%` }}
                        transition={{ duration: 0.9, delay: index * 0.08 }}
                        style={{ backgroundColor: COLORS[index % COLORS.length] }}
                      />
                    </div>
                  </div>
                ))}
            </div>
          ) : (
            <EmptyState>
              No open task workload is available.
            </EmptyState>
          )}
        </ChartPanel>
      )}

    </motion.div>
  );
}
