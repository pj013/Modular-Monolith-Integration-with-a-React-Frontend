import { useEffect, useState } from 'react'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const LOW_STOCK_THRESHOLD = 5 // mirrors backend app.inventory.low-stock-threshold default

export default function App() {
  const [products, setProducts] = useState([])
  const [orders, setOrders] = useState([])
  const [notifications, setNotifications] = useState([])

  const [cart, setCart] = useState([]) // [{ productId, quantity }]
  const [selectedProductId, setSelectedProductId] = useState('')
  const [selectedQuantity, setSelectedQuantity] = useState(1)

  const [lastResult, setLastResult] = useState(null)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [cancellingId, setCancellingId] = useState(null)

  const refreshAll = () => {
    fetch(`${API_BASE_URL}/api/inventory`).then((r) => r.json()).then(setProducts).catch(() => {})
    fetch(`${API_BASE_URL}/api/orders`).then((r) => r.json()).then(setOrders).catch(() => {})
    fetch(`${API_BASE_URL}/api/notifications`).then((r) => r.json()).then(setNotifications).catch(() => {})
  }

  useEffect(() => {
    refreshAll()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (products.length > 0 && !selectedProductId) {
      setSelectedProductId(products[0].productId)
    }
  }, [products, selectedProductId])

  const addToCart = () => {
    if (!selectedProductId || selectedQuantity < 1) return
    setCart((prev) => {
      const existing = prev.find((i) => i.productId === selectedProductId)
      if (existing) {
        return prev.map((i) =>
          i.productId === selectedProductId ? { ...i, quantity: i.quantity + Number(selectedQuantity) } : i
        )
      }
      return [...prev, { productId: selectedProductId, quantity: Number(selectedQuantity) }]
    })
  }

  const removeFromCart = (productId) => {
    setCart((prev) => prev.filter((i) => i.productId !== productId))
  }

  const submitOrder = async () => {
    if (cart.length === 0) return
    setSubmitting(true)
    setError('')
    setLastResult(null)
    try {
      const res = await fetch(`${API_BASE_URL}/api/orders`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ items: cart }),
      })
      const data = await res.json()
      if (!res.ok) {
        setError(data.reason || `Request failed (${res.status})`)
      } else {
        setLastResult(data)
        setCart([])
      }
      refreshAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  const cancelOrder = async (orderId) => {
    setCancellingId(orderId)
    setError('')
    try {
      const res = await fetch(`${API_BASE_URL}/api/orders/${orderId}/cancel`, { method: 'POST' })
      const data = await res.json()
      if (!res.ok) {
        setError(data.reason || `Cancel failed (${res.status})`)
      }
      refreshAll()
    } catch (err) {
      setError(err.message)
    } finally {
      setCancellingId(null)
    }
  }

  const productName = (productId) => products.find((p) => p.productId === productId)?.name || productId

  return (
    <div className="page">
      <h1>Shop + Inventory</h1>
      <p className="subtitle">
        Order module (in-process) → Inventory module → Supabase (Postgres), with
        Order → Notification via domain events
      </p>

      <div className="grid">
        {/* Cart / order form */}
        <section className="card">
          <h2>Build an order</h2>
          <div className="add-row">
            <select value={selectedProductId} onChange={(e) => setSelectedProductId(e.target.value)}>
              {products.map((p) => (
                <option key={p.productId} value={p.productId}>
                  {p.productId} — {p.name} ({p.stock} in stock)
                </option>
              ))}
            </select>
            <input
              type="number"
              min="1"
              value={selectedQuantity}
              onChange={(e) => setSelectedQuantity(e.target.value)}
            />
            <button type="button" onClick={addToCart} disabled={!selectedProductId}>
              Add
            </button>
          </div>

          {cart.length === 0 ? (
            <p className="empty">Cart is empty — add an item above.</p>
          ) : (
            <ul className="cart-list">
              {cart.map((item) => (
                <li key={item.productId}>
                  <span>
                    {item.productId} — {productName(item.productId)} × {item.quantity}
                  </span>
                  <button type="button" className="link-button" onClick={() => removeFromCart(item.productId)}>
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          )}

          <button type="button" onClick={submitOrder} disabled={submitting || cart.length === 0}>
            {submitting ? 'Placing order…' : 'Place order'}
          </button>

          {error && <div className="result error">{error}</div>}

          {lastResult && (
            <div className={`result ${lastResult.status === 'CONFIRMED' ? 'success' : 'rejected'}`}>
              <div className="status">
                {lastResult.status}
                {lastResult.orderId ? ` — Order #${lastResult.orderId}` : ''}
              </div>
              {lastResult.reason && <div className="reason">{lastResult.reason}</div>}
              <ul className="outcome-list">
                {lastResult.items.map((i) => (
                  <li key={i.productId}>
                    {i.productId} × {i.quantity}: {i.outcome}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>

        {/* Inventory dashboard */}
        <section className="card">
          <h2>Inventory</h2>
          <table className="inventory-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Name</th>
                <th>Stock</th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.productId} className={p.stock < LOW_STOCK_THRESHOLD ? 'low-stock' : ''}>
                  <td>{p.productId}</td>
                  <td>{p.name}</td>
                  <td>{p.stock}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        {/* Order history */}
        <section className="card">
          <h2>Order history</h2>
          {orders.length === 0 ? (
            <p className="empty">No orders yet.</p>
          ) : (
            <ul className="order-history">
              {orders.map((o) => (
                <li key={o.orderId} className={`order-row order-${o.status.toLowerCase()}`}>
                  <div className="order-row-header">
                    <span>
                      #{o.orderId} — {o.status}
                    </span>
                    {o.status === 'CONFIRMED' && (
                      <button
                        type="button"
                        className="link-button"
                        onClick={() => cancelOrder(o.orderId)}
                        disabled={cancellingId === o.orderId}
                      >
                        {cancellingId === o.orderId ? 'Cancelling…' : 'Cancel'}
                      </button>
                    )}
                  </div>
                  {o.reason && <div className="order-reason">{o.reason}</div>}
                  <ul className="order-items">
                    {o.items.map((i, idx) => (
                      <li key={idx}>
                        {i.productId} × {i.quantity}
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Notification feed */}
        <section className="card">
          <h2>Activity feed</h2>
          {notifications.length === 0 ? (
            <p className="empty">No notifications yet.</p>
          ) : (
            <ul className="notification-feed">
              {notifications.map((n) => (
                <li key={n.notificationId} className={n.message.includes('reorder needed') ? 'notif-warning' : ''}>
                  {n.message}
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}
