import { useEffect, useState } from 'react'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export default function App() {
  const [products, setProducts] = useState([])
  const [productId, setProductId] = useState('')
  const [quantity, setQuantity] = useState(1)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loadingProducts, setLoadingProducts] = useState(true)
  const [submitting, setSubmitting] = useState(false)

  const loadInventory = () => {
    setLoadingProducts(true)
    fetch(`${API_BASE_URL}/api/inventory`)
      .then((res) => {
        if (!res.ok) throw new Error(`Failed to load inventory (${res.status})`)
        return res.json()
      })
      .then((data) => {
        setProducts(data)
        if (data.length > 0 && !productId) {
          setProductId(data[0].productId)
        }
        setError('')
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoadingProducts(false))
  }

  useEffect(() => {
    loadInventory()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSubmitting(true)
    setResult(null)
    setError('')

    try {
      const res = await fetch(`${API_BASE_URL}/api/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ productId, quantity: Number(quantity) }),
      })
      const data = await res.json()

      if (!res.ok) {
        setError(data.reason || `Request failed (${res.status})`)
      } else {
        setResult(data)
      }

      // Refresh the dropdown's stock numbers whether confirmed or rejected
      loadInventory()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="page">
      <h1>Order an item</h1>
      <p className="subtitle">
        Order module (in-process) → Inventory module → Supabase (Postgres)
      </p>

      <form onSubmit={handleSubmit} className="order-form">
        <label>
          Product
          <select
            value={productId}
            onChange={(e) => setProductId(e.target.value)}
            disabled={loadingProducts || products.length === 0}
          >
            {products.map((p) => (
              <option key={p.productId} value={p.productId}>
                {p.productId} — {p.name} ({p.stock} in stock)
              </option>
            ))}
          </select>
        </label>

        <label>
          Quantity
          <input
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </label>

        <button type="submit" disabled={submitting || !productId}>
          {submitting ? 'Placing order…' : 'Place order'}
        </button>
      </form>

      {error && <div className="result error">{error}</div>}

      {result && (
        <div className={`result ${result.status === 'CONFIRMED' ? 'success' : 'rejected'}`}>
          <div className="status">{result.status}</div>
          {result.reason && <div className="reason">{result.reason}</div>}
          {result.inventory && (
            <div className="inventory">
              {result.inventory.name} — {result.inventory.stock} left in stock
            </div>
          )}
        </div>
      )}
    </div>
  )
}
