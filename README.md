# Event-Sourced Ledger

A production-quality **event-sourced bank transaction ledger** built with Java 17, Spring Boot 3, PostgreSQL, and Redis. Every state change is captured as an immutable event, enabling full audit trails, time-travel debugging, and projection rebuilds.

## Features

- **Event Sourcing & CQRS**: Immutable append-only log with separate read (projections) and write models (aggregates).
- **Projections**: Account balances and transaction histories are derived dynamically from events.
- **Auto-Evicting Cache**: Redis-backed distributed caching for read query endpoints.
- **FX Conversion**: Real-time cross-currency transfer support.

## Getting Started

Configure database and Redis credentials in `.env`, then run the server:

```bash
mvn spring-boot:run
```

The server starts on `http://localhost:3000`. The SQL schema is auto-initialized on startup.

## API Endpoints

- `POST /api/accounts` - Open account
- `GET /api/accounts` - List accounts
- `GET /api/accounts/{id}` - Get account details
- `POST /api/accounts/{id}/deposit` - Deposit money
- `POST /api/accounts/{id}/withdraw` - Withdraw money
- `POST /api/accounts/{id}/freeze` / `unfreeze` / `close` - Manage account status
- `POST /api/transfers` - Transfer money between accounts
- `GET /api/accounts/{id}/events` - View raw event stream
- `POST /api/admin/rebuild-projections` - Rebuild all read models

## Quick Start Example

```bash
# 1. Open an Account
curl -X POST http://localhost:3000/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"ownerName": "Alice", "currency": "USD", "initialDeposit": 1000}'

# 2. Deposit Money
curl -X POST http://localhost:3000/api/accounts/{accountId}/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount": 500, "description": "Salary"}'

# 3. View the Event Stream (Time-Travel!)
curl http://localhost:3000/api/accounts/{accountId}/events
```

## Testing

Run unit and integration tests (Docker required for Testcontainers):

```bash
mvn test
```

## License
MIT
