import { motion } from "framer-motion";
import brandImage from "../assets/opsvista-brand.png";
import {
  freestyleRevealVariants,
  freestyleStaggerVariants,
} from "../motionPresets.js";

export default function BrandPanel() {
  return (
    <motion.section
      className="login__brand container-fluid d-flex flex-column justify-content-between"
      aria-label="OpsVista project and operations analytics"
      initial="hidden"
      animate="visible"
      variants={freestyleStaggerVariants}
    >
      <motion.div
        className="login__brand-top d-flex align-items-center justify-content-between flex-wrap gap-3"
        variants={freestyleStaggerVariants}
      >
        <motion.img
          className="login__brand-image"
          src={brandImage}
          alt="OpsVista — Every Project, Every Ticket, Every Insight — Track, Analyze & Manage Everything in One Place."
          variants={freestyleRevealVariants}
          whileHover={{ rotate: -1, scale: 1.015 }}
        />
        <motion.span
          className="login__badge badge rounded-pill"
          variants={freestyleRevealVariants}
        >
          Project workspace
        </motion.span>
      </motion.div>

      <motion.div
        className="login__intro"
        variants={freestyleStaggerVariants}
      >
        <motion.p className="login__eyebrow" variants={freestyleRevealVariants}>
          PROJECT &amp; OPERATIONS ANALYTICS
        </motion.p>

        <motion.ul
          className="login__roles list-group list-group-flush"
          variants={freestyleStaggerVariants}
        >
          <motion.li className="login__role list-group-item d-flex align-items-center gap-3" variants={freestyleRevealVariants}>
            <span className="login__role-index badge rounded-pill">01</span>
            <div><strong>Super User</strong><span>Manages users and sees every project</span></div>
          </motion.li>
          <motion.li className="login__role list-group-item d-flex align-items-center gap-3" variants={freestyleRevealVariants}>
            <span className="login__role-index badge rounded-pill">02</span>
            <div><strong>Admin</strong><span>Runs projects and reviews reports</span></div>
          </motion.li>
          <motion.li className="login__role list-group-item d-flex align-items-center gap-3" variants={freestyleRevealVariants}>
            <span className="login__role-index badge rounded-pill">03</span>
            <div><strong>Project Lead</strong><span>Tracks assigned tasks</span></div>
          </motion.li>
        </motion.ul>
      </motion.div>

      <motion.p className="login__footer" variants={freestyleRevealVariants}>
        A clearer view of work, from kickoff to delivery.
      </motion.p>
      <motion.div
        className="login__brand-orb"
        aria-hidden="true"
        animate={{ x: [0, 20, 0], y: [0, -16, 0], scale: [1, 1.08, 1] }}
        transition={{ duration: 12, repeat: Infinity, ease: "easeInOut" }}
      />
    </motion.section>
  );
}
