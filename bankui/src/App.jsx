import { Link, Route, Routes } from 'react-router'

import CustomerList from './components/CustomerList.jsx'
import EditCustomerPage from './pages/EditCustomerPage.jsx'

/*
 * The root component. It owns layout and the route table, and nothing else - no fetching,
 * no state. Same instinct as the controller in the API: this layer arranges, it does not
 * decide.
 *
 * <Routes> renders exactly one of its children - whichever <Route> best matches the current
 * URL. It is a switch, not a list.
 *
 * The route table is the front end's equivalent of @RequestMapping: a declaration of which
 * paths exist and what answers them. And like the API, an unmatched path gets a deliberate
 * answer rather than a blank screen - path="*" is the catch-all, the 404 of the client side.
 */
function App() {
  return (
    <>
      {/* Link, not <a href>. An <a> makes the browser fetch the page again from scratch,
          discarding the running app and every piece of state in it. Link changes the URL
          through the History API and lets React re-render - the whole reason a single-page
          app is single-page. */}
      <h1><Link to="/">Bank App</Link></h1>

      <Routes>
        {/* :id is a URL parameter, not a literal. It matches any single segment and hands
            the value to the component via useParams. This is what makes a route a route
            rather than a page name: the URL carries state. */}
        <Route path="/" element={<CustomerList />} />
        <Route path="/customers/:id/edit" element={<EditCustomerPage />} />
        <Route path="*" element={<p>No such page.</p>} />
      </Routes>
    </>
  )
}

export default App
