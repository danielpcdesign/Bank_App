import CustomerList from './components/CustomerList.jsx'

// The root component. It owns layout and nothing else - no fetching, no state.
// Same instinct as the controller in the API: this layer arranges, it does not decide.
function App() {
  return (
    <>
      <h1>Bank App</h1>
      <CustomerList />
    </>
  )
}

export default App
