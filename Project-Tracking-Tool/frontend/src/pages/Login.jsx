
import { useState } from "react";
import { motion } from "framer-motion";
import {
  login,
  submitAccountRequest,
  requestPasswordReset,
} from "../auth/authService.js";
import {
  freestyleFormVariants,
  freestyleInteractiveMotion,
  freestyleRevealVariants,
  freestyleSpring,
} from "../motionPresets.js";
import BrandPanel from "./BrandPanel.jsx";
import "./Login.css";
import "./Login-modern.css";

export default function Login({ onSuccess }) {
  const [mode, setMode] = useState("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(false);

  function changeMode(nextMode) {
    setMode(nextMode);
    setError("");
    setNotice("");
    setPassword("");
    setEmail("");
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setNotice("");
    setLoading(true);

    try {
      if (mode === "login") {
        if (!username.trim() || !password) {
          throw new Error("Enter your username and password.");
        }

        const user = await login(username, password);
        onSuccess(user);

      } else if (mode === "create") {
        if (!name.trim() || !username.trim() || !email.trim()) {
          throw new Error("Enter your name, requested username, and email address.");
        }

        if (!/^[a-z0-9._-]{3,30}$/i.test(username.trim())) {
          throw new Error("Username must be 3-30 characters using letters, numbers, dots, underscores, or hyphens.");
        }

        await submitAccountRequest({ name, username, email });

        setUsername(username.trim().toLowerCase());
        setPassword("");
        setName("");
        setEmail("");
        setNotice("Your enquiry was sent to the Super User. If approved, they will create your account and share your sign-in details.");
        setMode("login");

      } else if (mode === "forgot") {
        if (!email.trim()) {
          throw new Error("Enter your registered email address.");
        }

        await requestPasswordReset(email);

        setNotice("If an account exists with that email address, a password reset link has been sent. Please check your inbox.");
        setEmail("");

      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  const title =
    mode === "create"
      ? "Request an account"
      : mode === "forgot"
        ? "Forgot password?"
        : "Sign in";

  return (
    <motion.div
      className="login"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.35 }}
    >
      <BrandPanel />

      <motion.main
        className="login__panel"
        initial={{ opacity: 0, x: 24 }}
        animate={{ opacity: 1, x: 0 }}
        transition={{ ...freestyleSpring, delay: 0.1 }}
      >
        <motion.form
          className="login__form card shadow-sm"
          onSubmit={handleSubmit}
          noValidate
          variants={freestyleFormVariants}
          initial="hidden"
          animate="visible"
        >
          <motion.h1 className="mb-0" variants={freestyleRevealVariants}>{title}</motion.h1>

          <motion.p className="login__lead form-text mb-0" variants={freestyleRevealVariants}>
            {mode === "create"
              ? "Send an account enquiry to the Super User. Approved accounts are created by the Super User."
              : mode === "forgot"
                ? "Enter the email address associated with your account. We'll send you a password reset link."
                : "Use the username and password provided to you."}
          </motion.p>

          {error && (
            <motion.p className="login__error alert alert-danger" role="alert" variants={freestyleRevealVariants}>{error}</motion.p>
          )}

          {notice && (
            <motion.p className="login__notice alert alert-warning" role="status" variants={freestyleRevealVariants}>{notice}</motion.p>
          )}

          {mode === "create" && (
            <motion.label className="field" variants={freestyleRevealVariants}>
              <span className="form-label">Full name</span>
              <input
                className="form-control"
                type="text"
                name="fullName"
                autoComplete="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </motion.label>
          )}

          {mode === "forgot" ? (
            <motion.label className="field" variants={freestyleRevealVariants}>
              <span className="form-label">Registered email address</span>
              <input
                className="form-control"
                type="email"
                name="email"
                autoComplete="email"
                autoFocus
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </motion.label>
          ) : mode !== "reset" && (
            <motion.label className="field" variants={freestyleRevealVariants}>
              <span className="form-label">{mode === "create" ? "Requested username" : "Username"}</span>
              <input
                className="form-control"
                type="text"
                name="username"
                autoComplete="username"
                autoFocus
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </motion.label>
          )}

          {mode === "login" && (
            <motion.label className="field" variants={freestyleRevealVariants}>
              <span className="form-label">Password</span>
              <div className="field__password input-group">
                <input
                  className="form-control"
                  type={showPassword ? "text" : "password"}
                  name="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />

                <button
                  type="button"
                  className="field__toggle btn btn-outline-secondary"
                  onClick={() => setShowPassword((s) => !s)}
                  aria-pressed={showPassword}
                >
                  {showPassword ? "Hide" : "Show"}
                </button>
              </div>
            </motion.label>
          )}

          {mode === "create" && (
            <>
              <motion.label className="field" variants={freestyleRevealVariants}>
                <span className="form-label">Email address</span>
                <input
                  className="form-control"
                  type="email"
                  name="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </motion.label>

              <motion.p className="auth__note" variants={freestyleRevealVariants}>
                The Super User reviews enquiries and creates approved accounts. Do not enter a password here.
              </motion.p>
            </>
          )}

          {mode === "login" && (
            <motion.button
              type="button"
              className="auth__link auth__forgot btn btn-link"
              onClick={() => changeMode("forgot")}
              variants={freestyleRevealVariants}
              {...freestyleInteractiveMotion}
            >
              Forgot password?
            </motion.button>
          )}

          <motion.button
            type="submit"
            className="login__submit btn btn-warning"
            disabled={loading}
            variants={freestyleRevealVariants}
            {...freestyleInteractiveMotion}
          >
            {loading
              ? "Please wait..."
              : mode === "create"
                ? "Send enquiry"
                : mode === "forgot"
                  ? "Send reset link"
                  : "Sign in"}
          </motion.button>

          {mode === "login" ? (
            <motion.p className="auth__switch" variants={freestyleRevealVariants}>
              Need an account?{" "}
              <button
                type="button"
                className="auth__link btn btn-link"
                onClick={() => changeMode("create")}
              >
                Send an enquiry
              </button>
              {" "}to the Super User.
            </motion.p>
          ) : (
            <motion.button
              type="button"
              className="auth__link auth__back btn btn-link"
              onClick={() => changeMode("login")}
              variants={freestyleRevealVariants}
              {...freestyleInteractiveMotion}
            >
              Back to sign in
            </motion.button>
          )}
        </motion.form>
      </motion.main>
    </motion.div>
  );
}
