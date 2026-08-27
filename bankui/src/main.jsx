import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import './index.css'
import App from './App.jsx'

/*
 * BrowserRouter wraps the whole app because routing is ambient: any component at any depth
 * can ask "what is the current URL" or "take me somewhere else". That answer has to come
 * from somewhere above all of them, so the provider sits at the root - the same reason a
 * Spring bean is registered in the context rather than passed by hand to every caller.
 *
 * "Browser" means it uses the real History API, so URLs look like /customers/2/edit with no
 * #. The cost is a server requirement: a deep link is a genuine GET for that path, and the
 * server must answer index.html rather than 404. Vite does this in dev automatically. In
 * production it has to be configured - Phase 9.
 */
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
