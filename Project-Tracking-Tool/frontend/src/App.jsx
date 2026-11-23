
import { useState } from "react";
import { MotionConfig } from "framer-motion";
import { getStoredUser, logout } from "./auth/authService.js";
import Dashboard from "./pages/Dashboard.jsx";
import Login from "./pages/Login.jsx";
import ResetPassword from "./pages/ResetPassword.jsx";

export default function App() {
  const [user, setUser] = useState(() => getStoredUser());

  function handleLogout() {
    logout();
    setUser(null);
  }

  // Show the reset-password page when the email link is opened
  if (window.location.pathname === "/reset-password") {
    return (
      <MotionConfig reducedMotion="user">
        <ResetPassword />
      </MotionConfig>
    );
  }

  if (!user) {
    return (
      <MotionConfig reducedMotion="user">
        <Login onSuccess={setUser} />
      </MotionConfig>
    );
  }

  return (
    <MotionConfig reducedMotion="user">
      <Dashboard user={user} onLogout={handleLogout} onUserUpdate={setUser} />
    </MotionConfig>
  );
}
