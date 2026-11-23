import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import { applyTheme, initialTheme } from './theme.jsx'

// Step 40: set the saved theme before the first paint, so the page never flashes the wrong colours.
applyTheme(initialTheme())

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)