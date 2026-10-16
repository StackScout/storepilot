# StorePilot

StorePilot is a multi-vendor e-commerce marketplace and appointment-booking platform connecting independent local sellers with buyers.

This repository is organized as a monorepo containing:
- **`backend/`**: Spring Boot 3 + Kotlin REST API with PostgreSQL, Flyway migrations, and AWS Cognito authentication.
- **`app/`**: Next.js 16 (App Router) web application for marketplace browsing, checkout, and seller/admin dashboards.
- **`mobile/`**: React Native / Expo (SDK 57) mobile application for iOS and Android.
- **`packages/shared-api`**: Shared TypeScript contracts and API clients consumed by web and mobile.
- **`infra/`**: CloudFormation templates, Docker compose configurations, and deployment scripts.

---

## Prerequisites

Before starting, ensure your local development machine (macOS recommended) has the following installed:

| Tool | Version | Purpose |
|---|---|---|
| **Node.js** | v20+ or v22+ (LTS) | Web frontend, mobile app, and shared packages |
| **Java (JDK)** | **Java 21** | Spring Boot backend compilation and execution |
| **Docker** | Latest | Running the local PostgreSQL database (or use Homebrew Postgres) |
| **Xcode & CLI Tools** | Latest | Building and running the mobile app in the iOS Simulator |
| **CocoaPods** | Latest | Managing native iOS dependencies |
| **Watchman** | Latest | Recommended by React Native for efficient file watching |

### Quick installation on macOS (via Homebrew):
```bash
# Package manager & core tools
brew install node watchman cocoapods

# Java 21
brew install openjdk@21
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk

# Ensure Java 21 is on your PATH (add to ~/.zshrc or ~/.bash_profile):
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## 1. Initial Repository Setup

Install all workspace dependencies from the root directory:

```bash
git clone <repository-url>
cd <repository-directory>

# Installs dependencies across app, mobile, and packages/shared-api
npm install
```

---

## 2. Start PostgreSQL Database

The local development backend defaults to connecting to PostgreSQL at `localhost:5433` (database: `storepilot`, user: `storepilot`, password: `storepilot`).

### Option A: Using Docker (Recommended)
```bash
docker run -d \
  --name storepilot-postgres \
  -p 5433:5432 \
  -e POSTGRES_DB=storepilot \
  -e POSTGRES_USER=storepilot \
  -e POSTGRES_PASSWORD=storepilot \
  postgres:16-alpine
```

### Option B: Using Native Homebrew PostgreSQL
If your local Postgres runs on default port `5432`, either configure it to listen on `5433` or update `DB_URL` in your backend configuration:
```bash
brew install postgresql@16
brew services start postgresql@16

# Create user and database
createuser -s storepilot
psql -U storepilot -d postgres -c "ALTER USER storepilot WITH PASSWORD 'storepilot';"
createdb -U storepilot storepilot
```

---

## 3. Run the Backend API

### 1. Configure Local Secrets (`config.yml`)
The backend loads local development credentials from `backend/src/main/resources/config.yml` (this file is gitignored to avoid committing secrets).

Create `backend/src/main/resources/config.yml`:

```yaml
DB_URL: jdbc:postgresql://localhost:5433/storepilot
DB_USERNAME: storepilot
DB_PASSWORD: storepilot

# Sri Lanka payment gateway (optional/sandbox)
PAYHERE_MERCHANT_ID: 1234567
PAYHERE_MERCHANT_SECRET: test_merchant_secret
PAYHERE_SANDBOX: true
PAYHERE_NOTIFY_URL: http://localhost:8080/api/payments/payhere/notify
PAYHERE_RETURN_URL_BASE: http://localhost:3000/orders

NOTIFICATIONS_FRONTEND_BASE_URL: http://localhost:3000

# AWS Cognito (Dev User Pool)
COGNITO_REGION: ap-southeast-2
COGNITO_USER_POOL_ID: your_cognito_user_pool_id
COGNITO_CLIENT_ID: your_cognito_client_id

# Cognito Hosted UI (Google OAuth)
COGNITO_OAUTH_DOMAIN: your_domain.auth.ap-southeast-2.amazoncognito.com
COGNITO_OAUTH_CLIENT_ID: your_oauth_client_id
COGNITO_OAUTH_CLIENT_SECRET: your_oauth_client_secret
COGNITO_OAUTH_REDIRECT_URI: http://localhost:8080/api/auth/google/callback
```

### 2. Launch Spring Boot
```bash
cd backend
./gradlew bootRun
```

On first startup, Flyway will run all database schema migrations automatically. The API will be available at:
**`http://localhost:8080`** (Health check: `http://localhost:8080/actuator/health`).

---

## 4. Run the Web Frontend (Next.js)

### 1. Configure Frontend Environment
Create `app/.env.local`:

```env
# Optional: defaults to http://localhost:8080 if omitted
NEXT_PUBLIC_API_URL=http://localhost:8080

# Cognito credentials (must match the backend's Cognito pool for JWT verification)
NEXT_PUBLIC_COGNITO_REGION=ap-southeast-2
NEXT_PUBLIC_COGNITO_USER_POOL_ID=your_cognito_user_pool_id
```

### 2. Start Next.js Development Server
From the root directory or `app/`:

```bash
npm run dev --workspace=app
# or: cd app && npm run dev
```

The web application will be accessible at:
**`http://localhost:3000`**

---

## 5. Run the Mobile App (React Native / Expo)

### 1. Install iOS Native Pods
Because the mobile app uses native modules, compile the CocoaPods dependencies once:

```bash
cd mobile/ios
pod install
cd ..
```

### 2. Configure Mobile Environment
Create `mobile/.env`:

- **For iOS Simulator (Mac shares localhost):**
  ```env
  EXPO_PUBLIC_API_BASE_URL=http://localhost:8080
  ```
- **For Android Emulator (loopback alias):**
  ```env
  EXPO_PUBLIC_API_BASE_URL=http://10.0.2.2:8080
  ```
- **For Physical Device on Wi-Fi:**
  ```env
  EXPO_PUBLIC_API_BASE_URL=http://<YOUR_MAC_LAN_IP>:8080
  ```

*(Note: Do not include a trailing slash in `EXPO_PUBLIC_API_BASE_URL`).*

### 3. Launch the App
To start the native build and open the iOS Simulator:

```bash
cd mobile
npm run ios
```

Or start the Expo Metro bundler directly:
```bash
npx expo start -c
```
- Press **`i`** in the terminal to open on **iOS Simulator**.
- Press **`a`** to open on **Android Emulator**.

---

## Summary of Local Ports

| Service | Address |
|---|---|
| **PostgreSQL Database** | `localhost:5433` (or `5432`) |
| **Backend REST API** | `http://localhost:8080` |
| **Web Frontend (Next.js)** | `http://localhost:3000` |
| **Mobile Metro Bundler** | `http://localhost:8081` |
